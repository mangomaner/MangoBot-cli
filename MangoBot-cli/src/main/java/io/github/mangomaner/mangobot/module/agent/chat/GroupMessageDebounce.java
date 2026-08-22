package io.github.mangomaner.mangobot.module.agent.chat;

import io.github.mangomaner.mangobot.api.MangoAgentApi;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotGroupMessageEvent;
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
import java.util.stream.Collectors;

/**
 * 群消息 1 秒合并（leading-edge debounce）：
 *
 * <p>缓冲区收到第一条消息后启动 1 秒倒计时，倒计时结束无论后续又来了多少条，都一次性合并触发 bot；
 * 触发后新来的消息重新开始新一轮合并。这样即使每 0.8s 就有一条消息的高强度刷屏，bot 也能稳定每秒触发一次，
 * 不会出现消息一直刷新导致 debounce 永远等不到"安静"而卡死。
 *
 * <p>合并时按消息时间排序，而非到达顺序——消息异步投递，可能出现后发消息因处理快反而先到。
 *
 * <p>触发由 ChatOrchestrator 完成：若 bot 正在处理上一批，新一批会打断旧调用并基于最新内容续答。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupMessageDebounce {

    private static final long DEBOUNCE_MS = 1000L;

    private final GroupMessageContextBuilder contextBuilder;

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
     *
     * @param event 群消息事件
     * @param body  消息正文（parse_message）
     */
    public void enqueue(OneBotGroupMessageEvent event, String body) {
        String groupKey = event.getSelfId() + ":" + event.getGroupId();
        long time = event.getTime() > 0 ? event.getTime() * 1000L : System.currentTimeMillis();
        String envelope = contextBuilder.build(event, body);
        buffers.computeIfAbsent(groupKey, k -> new ArrayList<>())
                .add(new PendingMessage(time, envelope, event.getSelfId(), event.getGroupId()));
        // 该群已有倒计时则沿用；没有则从第一条消息开始计时 1 秒
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
            String merged = messages.stream()
                    .map(PendingMessage::envelope)
                    .collect(Collectors.joining("\n"));
            PendingMessage first = messages.get(0);
            ChatSessionVO session = MangoAgentApi.getSessionByBotIdAndChatId(
                    String.valueOf(first.botId()), String.valueOf(first.groupId()), SessionSource.GROUP);
            // 合并后发送者不再单一，信封自带发送者信息，name 用通用标识即可
            MangoAgentApi.streamChat(session.getId(), merged, "group").subscribe();
            log.info("Debounced {} group messages for {} in {}ms", messages.size(), groupKey, DEBOUNCE_MS);
        } catch (Exception e) {
            log.error("Failed to send merged group messages for {}", groupKey, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private record PendingMessage(long time, String envelope, long botId, long groupId) {
    }
}
