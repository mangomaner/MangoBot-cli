package io.github.mangomaner.mangobot.module.agent.chat;

import io.github.mangomaner.mangobot.module.agent.capability.tool.TurnContext;
import io.github.mangomaner.mangobot.module.agent.capability.tool.TurnContextRegistry;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeEvent;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeImage;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeProperties;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeSession;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeTurnInput;
import io.github.mangomaner.mangobot.module.file.model.domain.BotFiles;
import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * 跑一个聊天的一轮：把一批 {@link IncomingLine} 拼成一次 claude 调用，并登记轮次上下文。
 *
 * <p><b>每轮都现取会话，不缓存 {@link ClaudeSession}</b>：主模型换绑时
 * {@code ClaudeRuntimeManager.onModelRoleChanged} 会把会话整个换掉并关掉旧进程，
 * 缓存住就会往一个已经关掉的进程写。
 *
 * <p><b>每轮发给模型的是两块</b>：控制块（我方的指令，可能为空）+ 聊天记录（信封）。
 * 组装只走 {@link TurnPromptComposer} 一处，别在这里自己拼。
 *
 * <p>本方法跑在 {@code ChatInboxRegistry} 的轮次池线程上，所以这里允许阻塞等这一轮结束——
 * 派发线程那边只负责缓冲。等，是因为要靠 {@code result} 事件给 inbox 一个"这轮真的完了"的信号，
 * 也是因为发送工具需要的轮次上下文必须活到轮次结束。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatTurnRunner {

    /** 单张图的字节上限；超过就跳过，不打断这一轮。 */
    private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;

    private final ClaudeRuntimeManager runtimeManager;
    private final ClaudeRuntimeProperties properties;
    private final TurnControlRegistry turnControls;

    public void run(ChatTarget target, List<IncomingLine> batch) {
        if (batch.isEmpty()) {
            return;
        }
        String sessionKey = target.sessionKey();

        ClaudeSession session;
        try {
            session = runtimeManager.getOrCreate(String.valueOf(target.botId()),
                    target.runtimeSourceKey(), target.source());
        } catch (Exception e) {
            log.warn("取 claude 会话失败，这一批 {} 条消息没有回复：{}", batch.size(), sessionKey, e);
            return;
        }

        List<ClaudeImage> images = collectImages(batch);
        // 每轮的提示词只在这一处组装：控制块（我方指令）+ 聊天记录（信封）。
        // peek 只读——下面万一提交不出去（会话正忙），排队的控制不能就这么丢了。
        List<TurnControl> controls = turnControls.peek(sessionKey);
        String text = TurnPromptComposer.compose(controls, buildEnvelope(batch));
        TurnContext context = TurnContext.of(target.botId(), target.source(), target.targetId());

        TurnContextRegistry.begin(sessionKey, context);
        try {
            Optional<Flux<ClaudeEvent>> flux = session.trySendTurn(new ClaudeTurnInput(text, images));
            if (flux.isEmpty()) {
                log.info("会话正忙，这一批 {} 条消息没有提交：{}", batch.size(), sessionKey);
                return;
            }
            // 提交成功了才算"带上了"，这时才把一次性控制清掉；常驻的留着下一轮继续带
            turnControls.commitOnce(sessionKey);
            List<ClaudeEvent> events = flux.get()
                    .collectList()
                    .block(Duration.ofSeconds(properties.getTurnTimeoutSeconds() + 5L));
            summarize(sessionKey, events, context, images.size());
        } catch (Exception e) {
            log.warn("轮次执行失败：{}", sessionKey, e);
        } finally {
            TurnContextRegistry.end(sessionKey);
        }
    }

    // ========================================================================
    // 拼装
    // ========================================================================

    /**
     * 把这一批消息拼成一段信封文本，一行一条。
     *
     * <p>若某行回复的目标也在本批里，追加 {@code [回复上文]} 提示模型它接的是上面哪句。
     * 目标不在本批里的（那是历史消息，本轮明确不回补）什么都不加。
     */
    private String buildEnvelope(List<IncomingLine> batch) {
        StringBuilder sb = new StringBuilder();
        for (IncomingLine line : batch) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line.text());
            if (line.replyToMessageId() != null && isInBatch(batch, line.replyToMessageId())) {
                sb.append(" [回复上文]");
            }
        }
        return sb.toString();
    }

    private boolean isInBatch(List<IncomingLine> batch, long messageId) {
        for (IncomingLine line : batch) {
            if (line.messageId() == messageId) {
                return true;
            }
        }
        return false;
    }

    /**
     * 收集这一轮要送的图：按出现顺序读字节，最后只留最靠后的 N 张（越近越相关）。
     *
     * <p>读盘只在这里做——监听器在派发线程上，磁盘 IO 不许下派发线程。
     * 缺失 / 路径为空 / 扩展名不认识 / 超过上限的一律跳过并告警，不打断这一轮。
     */
    private List<ClaudeImage> collectImages(List<IncomingLine> batch) {
        int limit = properties.getMaxImagesPerTurn();
        if (limit <= 0) {
            return List.of();
        }

        List<ClaudeImage> collected = new ArrayList<>();
        for (IncomingLine line : batch) {
            for (BotFiles file : line.images()) {
                ClaudeImage image = readImage(file);
                if (image != null) {
                    collected.add(image);
                }
            }
        }
        if (collected.size() <= limit) {
            return collected;
        }
        return new ArrayList<>(collected.subList(collected.size() - limit, collected.size()));
    }

    private ClaudeImage readImage(BotFiles file) {
        String filePath = file.getFilePath();
        if (filePath == null || filePath.isBlank()) {
            log.warn("跳过一张没有本地路径的图：fileId={}", file.getFileId());
            return null;
        }
        String mediaType = FileUtils.mediaTypeOf(filePath);
        if (mediaType == null) {
            log.warn("跳过一张扩展名不认识的图：fileId={} path={}", file.getFileId(), filePath);
            return null;
        }
        try {
            Path resolved = FileUtils.resolvePath(filePath);
            long size = Files.size(resolved);
            if (size > MAX_IMAGE_BYTES) {
                log.warn("跳过一张过大的图（{} 字节 > {}）：fileId={}",
                        size, MAX_IMAGE_BYTES, file.getFileId());
                return null;
            }
            byte[] bytes = Files.readAllBytes(resolved);
            log.info("本轮带图：fileId={} mediaType={} bytes={}", file.getFileId(), mediaType, bytes.length);
            return new ClaudeImage(mediaType, Base64.getEncoder().encodeToString(bytes));
        } catch (IOException e) {
            log.warn("读图失败，跳过：fileId={} path={}", file.getFileId(), filePath, e);
            return null;
        }
    }

    /**
     * 一轮结束记一行 INFO：{@code subtype} / 发送条数 / 图片张数 / 正文长度。
     *
     * <p>"bot 不理我"就是靠这行查的：{@code subtype != success} 且一条都没发，说明这轮本身就失败了。
     *
     * <p>顺手把模型直接吐在正文里的字交给 {@link TurnSignal}：群聊里它本该调工具说话，正文非空
     * 是要看的信号。非模拟会话是空操作。
     */
    private void summarize(String sessionKey, List<ClaudeEvent> events, TurnContext context, int imageCount) {
        if (events == null) {
            log.warn("轮次没有结果事件：{}", sessionKey);
            return;
        }
        String subtype = "";
        StringBuilder assistantText = new StringBuilder();
        for (ClaudeEvent event : events) {
            if (event.isResult()) {
                subtype = event.resultSubtype();
            } else if (event.isAssistant()) {
                String text = event.assistantText();
                if (text != null && !text.isBlank()) {
                    assistantText.append(text);
                }
            }
        }
        TurnSignal.noteAssistantText(sessionKey, assistantText.toString());

        int sends = context.sends().get();
        String summary = String.format("轮次结束：session=%s subtype=%s 发送=%d 图片=%d",
                sessionKey, subtype, sends, imageCount);

        if (!"success".equals(subtype) && sends == 0) {
            log.warn("{}（一条都没发出去，这轮大概率没回复）", summary);
        } else {
            log.info(summary);
        }
    }
}
