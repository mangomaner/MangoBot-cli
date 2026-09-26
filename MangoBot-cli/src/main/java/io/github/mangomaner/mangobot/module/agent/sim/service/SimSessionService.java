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
import io.github.mangomaner.mangobot.module.agent.sim.harness.SimOneBotClient;
import io.github.mangomaner.mangobot.module.agent.sim.harness.SimSend;
import io.github.mangomaner.mangobot.module.agent.sim.model.domain.SimMessage;
import io.github.mangomaner.mangobot.module.agent.sim.model.domain.SimSession;
import io.github.mangomaner.mangobot.module.agent.sim.review.SimReviewer;
import io.github.mangomaner.mangobot.system.mapper.agent.SimMessageMapper;
import io.github.mangomaner.mangobot.system.mapper.agent.SimSessionMapper;
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

    /** 当前这一场；同一时刻最多一个。 */
    private final Map<Integer, ActiveSim> active = new ConcurrentHashMap<>();

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

    /** 开一场模拟：建虚拟连接、播种白名单、把上一场的对话上下文清掉、落一行 running 的会话。 */
    public SimSession start(String title, String personaLabel, String seedRef) {
        closeActive();

        // 没有专属配置行的 self_id 会被 MangoEventPublisher 的白名单整条拦掉，一条也喂不进去
        SimBotProfile.seedWhitelist();

        // 模拟用的是固定的会话键与工作目录，所以必须把上一场彻底清掉，否则这一场开局就会带着
        // 上一场的对话历史（现在会话是持久化的，光重建进程不够——{@code -c} 会把旧转录续回来）。
        // resetSession = 关进程 + 删该会话的 config 目录，正好是"这一场从零开始"。
        runtimeManager.resetSession(SimBotProfile.sessionKey());

        SimOneBotClient client = new SimOneBotClient(
                URI.create(MOCK_SERVER_URI), SimBotProfile.SELF_ID, SimImageIndex.of(List.of()));
        try {
            if (!client.connectBlocking(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("连不上 mock OneBot 服务端：" + MOCK_SERVER_URI
                        + "（检查 onebot_config 里 name=mock 那条是否启用）");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("连 mock 服务端时被中断", e);
        }

        SimSession session = new SimSession();
        session.setTitle(title == null || title.isBlank() ? "未命名模拟" : title);
        session.setSource(SessionSource.GROUP.getSourceKey());
        session.setBotId(String.valueOf(SimBotProfile.SELF_ID));
        session.setChatId(String.valueOf(SimBotProfile.GROUP_ID));
        session.setPersonaLabel(personaLabel);
        session.setSeedRef(seedRef);
        session.setStatus(STATUS_RUNNING);
        sessionMapper.insert(session);

        active.put(session.getId(), new ActiveSim(client));
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
        ActiveSim sim = requireActive(sessionId);
        SimSession session = requireSession(sessionId);

        String sessionKey = new ChatTarget(SimBotProfile.SELF_ID, SessionSource.GROUP,
                SimBotProfile.GROUP_ID).sessionKey();
        long memberQq = SimBotProfile.memberQq(speaker, qq);
        String nickname = SimBotProfile.memberName(speaker, qq);

        long waitMillis = (properties.getTurnTimeoutSeconds() + 5L) * 1000L
                + TURN_WAIT_MARGIN_MILLIS;

        appendPeerMessage(session, sim, nickname, memberQq, text);

        // 必须在发帧之前登记：晚一步就会错过这一轮的结束信号
        TurnSignal.expect(sessionKey);
        sim.client().sendRaw(SimFrames.groupMessage(SimBotProfile.SELF_ID, SimBotProfile.GROUP_ID,
                memberQq, nickname, text, sim.nextMessageId().getAndIncrement(),
                System.currentTimeMillis() / 1000L));

        Optional<String> assistantText;
        try {
            assistantText = TurnSignal.awaitTurn(sessionKey, waitMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等这一轮结束时被中断", e);
        }
        List<SimSend> sends = sim.client().drainSends();

        if (assistantText.isEmpty()) {
            throw new IllegalStateException("这一句发出后 " + waitMillis
                    + "ms 内没有任何轮次结束——消息没进链路？（检查 mock 服务端与白名单播种）");
        }

        appendBotMessages(session, sim, sends, assistantText.get());
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
            // 这场到此为止：把连接收掉，腾出唯一那个位置
            closeActive();
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

    private SimSession requireSession(Integer sessionId) {
        SimSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("没有这场模拟：" + sessionId);
        }
        return session;
    }

    private ActiveSim requireActive(Integer sessionId) {
        ActiveSim sim = active.get(sessionId);
        if (sim == null) {
            throw new IllegalStateException("这场模拟的连接已经收了（只有正在对练的那一场能继续喂）："
                    + sessionId);
        }
        return sim;
    }

    /** 收掉当前这一场与残留连接。没有残留时是空操作。 */
    private void closeActive() {
        for (Map.Entry<Integer, ActiveSim> entry : active.entrySet()) {
            try {
                entry.getValue().client().shutdown();
                log.info("收掉上一场模拟的连接：id={}", entry.getKey());
            } catch (Exception e) {
                log.warn("收模拟连接时出错：id={}", entry.getKey(), e);
            }
            active.remove(entry.getKey());
        }
    }

    private void appendPeerMessage(SimSession session, ActiveSim sim,
                                   String nickname, long qq, String text) {
        SimMessage message = new SimMessage();
        message.setSessionId(session.getId());
        message.setSeq(sim.nextSeq().getAndIncrement());
        message.setSpeaker(nickname);
        message.setSenderId(String.valueOf(qq));
        message.setRole("peer");
        message.setKind("text");
        message.setContent(text);
        message.setAtMs(sim.elapsedMillis());
        message.setCreateTime(new Date());
        messageMapper.insert(message);
    }

    private void appendBotMessages(SimSession session, ActiveSim sim, List<SimSend> sends,
                                   String assistantText) {
        for (SimSend send : sends) {
            SimMessage message = new SimMessage();
            message.setSessionId(session.getId());
            message.setSeq(sim.nextSeq().getAndIncrement());
            message.setSpeaker("bot");
            message.setSenderId(String.valueOf(SimBotProfile.SELF_ID));
            message.setRole("bot");
            message.setKind(send.kind());
            message.setContent(send.payload());
            message.setReplyTo(send.replyToId());
            message.setAtMs(sim.elapsedMillis());
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
            message.setSeq(sim.nextSeq().getAndIncrement());
            message.setSpeaker("bot");
            message.setSenderId(String.valueOf(SimBotProfile.SELF_ID));
            message.setRole("bot");
            message.setKind(KIND_NOTE);
            message.setContent(assistantText.trim());
            message.setAtMs(sim.elapsedMillis());
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

    /** 一场正在对练的模拟所持有的运行时状态。 */
    private record ActiveSim(SimOneBotClient client, long startedAtMillis,
                             AtomicInteger nextMessageId, AtomicInteger nextSeq) {

        ActiveSim(SimOneBotClient client) {
            this(client, System.currentTimeMillis(), new AtomicInteger(1), new AtomicInteger(1));
        }

        long elapsedMillis() {
            return System.currentTimeMillis() - startedAtMillis;
        }
    }
}
