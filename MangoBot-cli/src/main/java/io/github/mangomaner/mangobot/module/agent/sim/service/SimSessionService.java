package io.github.mangomaner.mangobot.module.agent.sim.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.module.agent.chat.ChatTarget;
import io.github.mangomaner.mangobot.module.agent.chat.TurnSignal;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeProperties;
import io.github.mangomaner.mangobot.module.agent.sim.harness.SimBotProfile;
import io.github.mangomaner.mangobot.module.agent.sim.harness.SimFrames;
import io.github.mangomaner.mangobot.module.agent.sim.harness.SimImageIndex;
import io.github.mangomaner.mangobot.module.agent.sim.harness.SimMediaServer;
import io.github.mangomaner.mangobot.module.agent.sim.harness.SimOneBotClient;
import io.github.mangomaner.mangobot.module.agent.sim.harness.SimSend;
import io.github.mangomaner.mangobot.module.agent.sim.model.domain.SimMessage;
import io.github.mangomaner.mangobot.module.agent.sim.model.domain.SimSession;
import io.github.mangomaner.mangobot.module.agent.sim.review.SimReviewer;
import io.github.mangomaner.mangobot.system.mapper.agent.SimMessageMapper;
import io.github.mangomaner.mangobot.system.mapper.agent.SimSessionMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 模拟对话的编排：开局、喂一句、读回来、打分、落库。
 *
 * <p><b>只有一场能同时跑。</b>这不是偷懒，是结构决定的：模拟用的虚拟 bot 号与群号是固定的
 * （{@link SimBotProfile}），而 claude 会话键是 {@code botId:source:chatId}——两场并发会共用一个
 * claude 进程与同一个 {@code TurnSignal} 登记位，信号会串场。所以 {@link #start} 会把上一场残留的
 * 连接收掉，天然只留一场。
 *
 * <p><b>它不走任何捷径。</b>群友的发言是当成真正从 QQ 来的入站帧注入的，bot 的回复是按 OneBot 协议
 * 真的发出去再从出站帧还原的，中间经过白名单、派发线程、防抖合并、轮次编排、claude、MCP 工具——
 * 与生产一模一样。生产代码为此一行未改。
 */
@Service
@Slf4j
public class SimSessionService {

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SCORING = "scoring";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_FAILED = "failed";

    /** 连 mock 服务端等的上限。 */
    private static final long CONNECT_TIMEOUT_MILLIS = 10_000L;

    /**
     * 一条"内心旁白"：bot 这一轮一个工具都没调（所以什么都没发出去），但正文里写了东西。
     *
     * <p>它是诊断信息，不是发言——见 {@link #transcript}。
     */
    private static final String KIND_NOTE = "note";

    /** 等一轮结束时额外留的余量：轮次本身有超时，这里只负责不让驱动线程无限等。 */
    private static final long TURN_WAIT_MARGIN_MILLIS = 30_000L;

    private static final String MOCK_SERVER_URI = "ws://127.0.0.1:8085/";

    private final SimSessionMapper sessionMapper;
    private final SimMessageMapper messageMapper;
    private final SimReviewer reviewer;
    private final ClaudeRuntimeManager runtimeManager;
    private final ClaudeRuntimeProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 当前正在驱动的那一场；同一时刻最多一个。
     *
     * <p>它只回答"现在喂的是哪一场"，<b>不代表连接的生命周期</b>——连接见 {@link #client}。
     */
    private final AtomicReference<Integer> currentSession = new AtomicReference<>();

    /**
     * 模拟台唯一的那条 OneBot 连接，懒建、复用、只在应用关闭时收。
     *
     * <p><b>为什么不做成"一场一条"</b>：实测（2026-09-27）同一个 JVM 里，重启后第一次 {@code start}
     * 正常、紧接着的第二次 {@code start} 必挂——客户端连得上、诊断口也报连接在，但那一轮永远不结束
     * （155 秒超时）。原因就是"关掉上一场、立刻用同一个 self_id 重连"这个快节奏重连。
     * 连接是基础设施，场次是数据，把两者绑在一起就会踩这个坑。
     */
    private volatile SimOneBotClient client;

    /** 图片源：让模型真的看到像素，见 {@link SimMediaServer}。懒建、长活。 */
    private volatile SimMediaServer mediaServer;

    /** 每条场次驱动轮的起始时刻，用于算 at_ms。 */
    private volatile long sessionStartedAtMillis = System.currentTimeMillis();

    /** 会话内的下一个顺序号。 */
    private final AtomicInteger nextSeq = new AtomicInteger(1);

    /**
     * 下一个 OneBot message_id。<b>同一个 JVM 内只增不减，绝不按场次重置</b>。
     *
     * <p>踩过的坑（2026-09-27，找了很久）：原来每开一场就重置成 1，于是第二场的消息 id 与第一场
     * 完全重复。而 {@code QqReplyHandler}（{@code @PluginPriority(0)}）里有按 message_id 的去重窗口，
     * 重复的直接 {@code return}——<b>消息永远到不了收件箱，那一轮永远不开始</b>，而且一声不吭
     * （不打日志、不报错），只表现为"连接在、say 却 155 秒超时"。
     *
     * <p>OneBot 的 message_id 本来就是全局唯一的，复用 id 是我方违规。所以这里跟会话无关：
     * 它是"这条连接发过的第几条消息"，不是"这一场的第几条"（后者是 {@link #nextSeq}）。
     */
    private final AtomicInteger nextMessageId = new AtomicInteger(1);

    public SimSessionService(SimSessionMapper sessionMapper, SimMessageMapper messageMapper,
                             SimReviewer reviewer, ClaudeRuntimeManager runtimeManager,
                             ClaudeRuntimeProperties properties, ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.reviewer = reviewer;
        this.runtimeManager = runtimeManager;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    // ========================================================================
    // 开局
    // ========================================================================

    /** 开一场模拟：确保连接在、播种白名单、把上一场的对话上下文清掉、落一行 running 的会话。 */
    public SimSession start(String title, String personaLabel, String seedRef) {

        // 没有专属配置行的 self_id 会被 MangoEventPublisher 的白名单整条拦掉，一条也喂不进去
        SimBotProfile.seedWhitelist();

        // 模拟用的是固定的会话键与工作目录，所以必须把上一场彻底清掉，否则这一场开局就会带着
        // 上一场的对话历史（现在会话是持久化的，光重建进程不够——{@code -c} 会把旧转录续回来）。
        // resetSession = 关进程 + 删该会话的 config 目录，正好是"这一场从零开始"。
        runtimeManager.resetSession(SimBotProfile.sessionKey());

        ensureClient();

        SimSession session = new SimSession();
        session.setTitle(title == null || title.isBlank() ? "未命名模拟" : title);
        session.setSource(SessionSource.GROUP.getSourceKey());
        session.setBotId(String.valueOf(SimBotProfile.SELF_ID));
        session.setChatId(String.valueOf(SimBotProfile.GROUP_ID));
        session.setPersonaLabel(personaLabel);
        session.setSeedRef(seedRef);
        session.setStatus(STATUS_RUNNING);
        sessionMapper.insert(session);

        currentSession.set(session.getId());
        sessionStartedAtMillis = System.currentTimeMillis();
        // seq 是"这一场的第几条"，按场重置；messageId 是"这条连接的第几条"，绝不重置（见字段注释）
        nextSeq.set(1);
        log.info("模拟对话已开局：id={} sessionKey={} persona={}",
                session.getId(), SimBotProfile.sessionKey(), personaLabel);
        return session;
    }

    // ========================================================================
    // 对练
    // ========================================================================

    /**
     * 以某个群友的身份发一句，然后等 bot 把这一轮说完。
     *
     * <p>等的是 {@link TurnSignal}——生产链路在一轮真正结束时喊的那一声。超时说明消息压根没进链路
     * （白名单没播种 / 服务端没起 / 会话取不到），必须当失败报出来，不能当成"这轮 bot 选择沉默"。
     *
     * @return bot 这一轮真正发出去的消息（可能为空＝它选择不说话，这是正常结果）
     */
    public List<SimSend> say(Integer sessionId, String speaker, Long qq, String text) {
        return say(sessionId, speaker, qq, text, null, 0);
    }

    /**
     * 以某个群友的身份发一条（可带图），然后等 bot 把这一轮说完。
     *
     * @param imageFileId 图片的 fileId（{@code bot_files} 里真实存在的）；null 表示纯文字
     * @param imageSubType 1/11 是表情包，其余当普通图；只在带图时有意义
     */
    public List<SimSend> say(Integer sessionId, String speaker, Long qq, String text,
                             String imageFileId, int imageSubType) {
        requireActive(sessionId);
        SimSession session = requireSession(sessionId);

        String sessionKey = new ChatTarget(SimBotProfile.SELF_ID, SessionSource.GROUP,
                SimBotProfile.GROUP_ID).sessionKey();
        long memberQq = SimBotProfile.memberQq(speaker, qq);
        String nickname = SimBotProfile.memberName(speaker, qq);

        long waitMillis = (properties.getTurnTimeoutSeconds() + 5L) * 1000L
                + TURN_WAIT_MARGIN_MILLIS;

        boolean withImage = imageFileId != null && !imageFileId.isBlank();
        String recorded = withImage
                ? (text == null || text.isBlank() ? "" : text + " ") + "[图片#" + imageFileId + "]"
                : text;
        appendPeerMessage(session, nickname, memberQq, recorded);

        // 必须在发帧之前登记：晚一步就会错过这一轮的结束信号
        TurnSignal.expect(sessionKey);
        int messageId = nextMessageId.getAndIncrement();
        long epoch = System.currentTimeMillis() / 1000L;
        String frame;
        if (withImage) {
            frame = SimFrames.groupMessageWithImage(SimBotProfile.SELF_ID, SimBotProfile.GROUP_ID,
                    memberQq, nickname, text, imageFileId,
                    ensureMediaServer().urlOf(imageFileId), imageSubType, messageId, epoch);
        } else {
            frame = SimFrames.groupMessage(SimBotProfile.SELF_ID, SimBotProfile.GROUP_ID,
                    memberQq, nickname, text, messageId, epoch);
        }
        client.sendRaw(frame);

        Optional<String> assistantText;
        try {
            assistantText = TurnSignal.awaitTurn(sessionKey, waitMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等这一轮结束时被中断", e);
        }
        List<SimSend> sends = client.drainSends();

        if (assistantText.isEmpty()) {
            throw new IllegalStateException("这一句发出后 " + waitMillis
                    + "ms 内没有任何轮次结束——消息没进链路？（检查 mock 服务端与白名单播种）");
        }

        appendBotMessages(session, sends, assistantText.get());
        return sends;
    }

    // ========================================================================
    // 评判
    // ========================================================================

    /**
     * 给这场对话打分，并把结果落库。
     *
     * @param seedExcerpt 参考语料摘录（可空）：judge 拿它当"这个群平时怎么说话"的尺子
     */
    public SimSession review(Integer sessionId, String seedExcerpt) {
        SimSession session = requireSession(sessionId);
        session.setStatus(STATUS_SCORING);
        sessionMapper.updateById(session);

        try {
            SimReviewer.SimReview review =
                    reviewer.review(sessionId, transcript(sessionId), seedExcerpt);
            if (review.error() != null) {
                session.setStatus(STATUS_FAILED);
                session.setVerdict("评判没跑成：" + review.error());
            } else {
                session.setStatus(STATUS_DONE);
                session.setVerdict(review.verdict());
                session.setScores(objectMapper.writeValueAsString(review.scores()));
            }
            sessionMapper.updateById(session);
            log.info("模拟对话评判完成：id={} status={} 角度数={}",
                    sessionId, session.getStatus(), review.scores().size());
            return session;
        } catch (Exception e) {
            log.error("模拟对话评判失败：id={}", sessionId, e);
            session.setStatus(STATUS_FAILED);
            session.setVerdict("评判失败：" + e);
            sessionMapper.updateById(session);
            return session;
        } finally {
            // 这场到此为止：只放下"当前场次"这个标记，**连接留着**——下一场还要用它，
            // 重连才是真正会出问题的动作（见 client 字段的注释）
            currentSession.set(null);
        }
    }

    // ========================================================================
    // 查询
    // ========================================================================

    public List<SimSession> listRecent(int limit) {
        // 按 id 倒序：SQLite 的 CURRENT_TIMESTAMP 是 UTC 文本，而自增 id 天然单调
        return sessionMapper.selectList(new LambdaQueryWrapper<SimSession>()
                .orderByDesc(SimSession::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    public Optional<SimSession> findSession(Integer sessionId) {
        return Optional.ofNullable(sessionMapper.selectById(sessionId));
    }

    public List<SimMessage> messages(Integer sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<SimMessage>()
                .eq(SimMessage::getSessionId, sessionId)
                .orderByAsc(SimMessage::getSeq));
    }

    // ========================================================================
    // 内部
    // ========================================================================

    /**
     * 当前挂着活连接的会话 id（同一时刻最多一个）。
     *
     * <p>给诊断用：出过"start 成功但 say 立刻说连接已收"的怪事，而这件事光看接口返回看不出来，
     * 必须能直接问一句"你手上到底有没有这场"。以后凡是这类"两边状态不一致"的怀疑，先看这里。
     */
    public List<Integer> activeSessionIds() {
        Integer current = currentSession.get();
        return current == null ? List.of() : List.of(current);
    }

    /** 这个会话在不在 active 里、它的连接是不是还开着。 */
    public Optional<String> activeState(Integer sessionId) {
        if (!sessionId.equals(currentSession.get())) {
            return Optional.empty();
        }
        SimOneBotClient c = client;
        return Optional.of(c != null && c.isOpen() ? "open" : "closed");
    }

    /** 懒建图片源；已经起着就直接用。 */
    private synchronized SimMediaServer ensureMediaServer() {
        SimMediaServer existing = mediaServer;
        if (existing != null) {
            return existing;
        }
        try {
            SimMediaServer created = SimMediaServer.start();
            mediaServer = created;
            return created;
        } catch (Exception e) {
            throw new IllegalStateException("启动模拟图片服务失败", e);
        }
    }

    /** 懒建连接；已经连着就直接用。 */
    private synchronized SimOneBotClient ensureClient() {
        SimOneBotClient existing = client;
        if (existing != null && existing.isOpen()) {
            return existing;
        }
        SimOneBotClient created = new SimOneBotClient(
                URI.create(MOCK_SERVER_URI), SimBotProfile.SELF_ID, SimImageIndex.of(List.of()));
        try {
            if (!created.connectBlocking(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("连不上 mock OneBot 服务端：" + MOCK_SERVER_URI
                        + "（检查 onebot_config 里 name=mock 那条是否启用）");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("连 mock 服务端时被中断", e);
        }
        client = created;
        log.info("模拟台连接已建立（长活，不随场次开关）：selfId={}", SimBotProfile.SELF_ID);
        return created;
    }

    @PreDestroy
    public void shutdown() {
        SimOneBotClient c = client;
        if (c != null) {
            c.shutdown();
        }
        SimMediaServer m = mediaServer;
        if (m != null) {
            m.stop();
        }
    }

    private SimSession requireSession(Integer sessionId) {
        SimSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("没有这场模拟：" + sessionId);
        }
        return session;
    }

    /** 这一场是不是正在驱动的那一场。 */
    private void requireActive(Integer sessionId) {
        if (!sessionId.equals(currentSession.get())) {
            throw new IllegalStateException("只有正在对练的那一场能继续喂："
                    + sessionId + "，当前是 " + currentSession.get());
        }
        if (client == null || !client.isOpen()) {
            throw new IllegalStateException("模拟台的连接没开着");
        }
    }


    private void appendPeerMessage(SimSession session, String nickname, long qq, String text) {
        SimMessage message = new SimMessage();
        message.setSessionId(session.getId());
        message.setSeq(nextSeq.getAndIncrement());
        message.setSpeaker(nickname);
        message.setSenderId(String.valueOf(qq));
        message.setRole("peer");
        message.setKind("text");
        message.setContent(text);
        message.setAtMs(System.currentTimeMillis() - sessionStartedAtMillis);
        message.setCreateTime(new Date());
        messageMapper.insert(message);
    }

    private void appendBotMessages(SimSession session, List<SimSend> sends,
                                   String assistantText) {
        for (SimSend send : sends) {
            SimMessage message = new SimMessage();
            message.setSessionId(session.getId());
            message.setSeq(nextSeq.getAndIncrement());
            message.setSpeaker("bot");
            message.setSenderId(String.valueOf(SimBotProfile.SELF_ID));
            message.setRole("bot");
            message.setKind(send.kind());
            message.setContent(send.payload());
            message.setReplyTo(send.replyToId());
            message.setAtMs(System.currentTimeMillis() - sessionStartedAtMillis);
            message.setCreateTime(new Date());
            messageMapper.insert(message);
        }

        if (sends.isEmpty() && assistantText != null && !assistantText.isBlank()) {
            // 这一轮它一个字都没发出去，但正文里写了东西——那是它的"内心旁白"，不是发言。
            //
            // 用 kind=note 与真正的发送严格区分开：第一次跑实测就栽在这里——旁白被当成了 bot 说过
            // 的话写进转录，judge 于是拿"这轮不出声，再补就成值班的了"这种自我调度去判真人感，
            // 结论整个偏掉。**沉默必须能被读成沉默。**
            SimMessage message = new SimMessage();
            message.setSessionId(session.getId());
            message.setSeq(nextSeq.getAndIncrement());
            message.setSpeaker("bot");
            message.setSenderId(String.valueOf(SimBotProfile.SELF_ID));
            message.setRole("bot");
            message.setKind(KIND_NOTE);
            message.setContent(assistantText.trim());
            message.setAtMs(System.currentTimeMillis() - sessionStartedAtMillis);
            message.setCreateTime(new Date());
            messageMapper.insert(message);
        }
    }

    /**
     * 把这场对话拼成给 judge 读的转录。
     *
     * <p>格式：群友一行；bot 说了话就紧跟一行 {@code bot:}；某条群友消息后面没有 bot 行，
     * 就表示 bot 那一轮<b>没说话</b>。这样"沉默"是能读出来的，而不是被省略掉。
     *
     * <p>{@code note} 行是内心旁白、不是发言，<b>不进转录</b>——否则 judge 会把"这轮不出声"这种
     * 自我调度当成 bot 说过的话来评。（要排查旁白请直接看 {@code sim_message}。）
     */
    private String transcript(Integer sessionId) {
        StringBuilder sb = new StringBuilder();
        for (SimMessage message : messages(sessionId)) {
            if (KIND_NOTE.equals(message.getKind())) {
                continue;
            }
            if ("bot".equals(message.getRole())) {
                sb.append("bot: ");
            } else {
                sb.append(message.getSpeaker()).append(": ");
            }
            switch (message.getKind() == null ? "text" : message.getKind()) {
                case "image" -> sb.append("[图片]");
                case "meme" -> sb.append("[表情包]");
                default -> sb.append(message.getContent());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

}
