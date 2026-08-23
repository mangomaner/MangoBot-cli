package io.github.mangomaner.mangobot.module.agent.chat;

import io.agentscope.core.message.ContentBlock;
import io.github.mangomaner.mangobot.api.MangoAgentApi;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotGroupMessageEvent;
import io.github.mangomaner.mangobot.module.agent.chat.GroupMessageContextBuilder.Envelope;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.ChatSessionVO;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 群消息 1 秒合并（leading-edge debounce）：
 *
 * <p>缓冲区收到第一条消息后启动 1 秒倒计时，倒计时结束无论后续又来了多少条，都一次性合并触发 bot；
 * 触发后新来的消息重新开始新一轮合并。这样即使高刷屏，bot 也能稳定每秒触发一次，不会卡死。
 *
 * <p>合并时按消息时间排序（非到达顺序）。每个信封生成该条的信封行 + 图片 ImageBlock，
 * 汇总为一条内容块序列交给 agent：每条消息的前置 TextBlock 后紧跟其图片块即可。
 * 「提到你 / 回复你」标记已内联在各条信封的元数据行里，仅在为 true 时出现，此处不再批量追加。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupMessageDebounce {

    private static final long DEBOUNCE_MS = 1000L;

    private final GroupMessageContextBuilder contextBuilder;
    private final MessageEnvelope messageEnvelope;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "group-message-debounce");
                t.setDaemon(true);
                return t;
            });

    /** groupKey(botId:groupId) -> 缓冲区消息（到达顺序，flush 时再按消息时间排序） */
    private final ConcurrentHashMap<String, List<PendingMessage>> buffers = new ConcurrentHashMap<>();
    /** groupKey -> 已安排的 flush 任务（同一 key 同时最多一个） */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingFlushes = new ConcurrentHashMap<>();

    /**
     * 接收一条群消息，并入 1 秒合并缓冲区。
     */
    public void enqueue(OneBotGroupMessageEvent event) {
        String groupKey = event.getSelfId() + ":" + event.getGroupId();
        long time = event.getTime() > 0 ? event.getTime() * 1000L : System.currentTimeMillis();
        Envelope envelope = contextBuilder.build(event);
        buffers.computeIfAbsent(groupKey, k -> new ArrayList<>())
                .add(new PendingMessage(time, envelope, event.getSelfId(), event.getGroupId()));
        pendingFlushes.computeIfAbsent(groupKey,
                k -> scheduler.schedule(() -> flush(groupKey), DEBOUNCE_MS, TimeUnit.MILLISECONDS));
    }

    private void flush(String groupKey) {
        pendingFlushes.remove(groupKey);
        List<PendingMessage> messages;
        try {
            messages = buffers.remove(groupKey);
        } catch (Exception e) {
            log.error("Failed to drain debounce buffer for {}", groupKey, e);
            return;
        }
        if (messages == null || messages.isEmpty()) {
            return;
        }
        try {
            messages.sort(Comparator.comparingLong(PendingMessage::time));

            List<ContentBlock> blocks = new ArrayList<>();
            for (PendingMessage message : messages) {
                Envelope envelope = message.envelope();
                blocks.add(messageEnvelope.text(envelope.line()));
                blocks.addAll(envelope.images());
            }

            PendingMessage first = messages.get(0);
            ChatSessionVO session = MangoAgentApi.getSessionByBotIdAndChatId(
                    String.valueOf(first.botId()), String.valueOf(first.groupId()), SessionSource.GROUP);
            MangoAgentApi.streamChat(session.getId(), blocks, "group").subscribe();
            log.info("Debounced {} group messages for {} in {}ms", messages.size(), groupKey, DEBOUNCE_MS);
        } catch (Exception e) {
            log.error("Failed to send merged group messages for {}", groupKey, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private record PendingMessage(long time, Envelope envelope, long botId, long groupId) {
    }
}
