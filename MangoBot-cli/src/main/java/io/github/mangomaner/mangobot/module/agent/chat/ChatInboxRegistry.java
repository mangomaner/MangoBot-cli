package io.github.mangomaner.mangobot.module.agent.chat;

import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 所有聊天的收件箱注册表，兼两个线程池的持有者。
 *
 * <p><b>两个池分工是刻意的：</b>定时池只负责"到点了，把缓冲交出去"，它自己绝不开轮次；
 * 轮次池才是真正阻塞着等 claude 出结果的地方。混在一起的话，防抖定时器会被慢轮次堵住，
 * 表现为"消息收进来了但迟迟不开跑"。
 *
 * <p>轮次池用有界队列 + {@code AbortPolicy}：满了就丢并告警，<b>绝不回压</b>到派发线程上——
 * 全 app 的 OneBot 事件共用那一个线程，堵住它就等于整个 bot 卡死。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatInboxRegistry {

    private static final int TURN_POOL_CORE = 2;
    private static final int TURN_POOL_MAX = 6;
    private static final int TURN_QUEUE_CAPACITY = 64;

    private final ChatTurnRunner turnRunner;
    private final ClaudeRuntimeProperties properties;

    private final Map<String, ChatInbox> inboxes = new ConcurrentHashMap<>();

    private final ScheduledExecutorService debounceTimer = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "chat-debounce");
        thread.setDaemon(true);
        return thread;
    });

    private final ThreadPoolExecutor turnPool = new ThreadPoolExecutor(
            TURN_POOL_CORE, TURN_POOL_MAX, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(TURN_QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(runnable, "chat-turn");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    /**
     * 投递一条入站消息。派发线程上调用。
     *
     * <p>这里只做入缓冲，不做任何发送、不做磁盘 IO；真正的轮次跑在 {@link #turnPool} 上。
     */
    public void submit(ChatTarget target, IncomingLine line) {
        ChatInbox inbox = inboxes.computeIfAbsent(target.sessionKey(), key -> new ChatInbox(target, this));
        inbox.offer(line);
    }

    /** 排一次防抖到期检查；代次过期时任务自己会无操作。 */
    void scheduleDebounce(ChatInbox inbox, long generation) {
        long delay = Math.max(0L, properties.getGroupDebounceMillis());
        debounceTimer.schedule(() -> {
            try {
                inbox.fireIfWindow(generation);
            } catch (Exception e) {
                log.error("防抖到期处理失败：{}", inbox.target().sessionKey(), e);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /** 把一批消息交给轮次池；池满时丢弃并告警（不回压派发线程）。 */
    void startTurn(ChatInbox inbox, List<IncomingLine> batch) {
        ChatTarget target = inbox.target();
        try {
            turnPool.execute(() -> {
                try {
                    turnRunner.run(target, batch);
                } catch (Exception e) {
                    log.error("轮次执行失败：{}", target.sessionKey(), e);
                } finally {
                    inbox.onTurnFinished();
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("轮次池已满，丢弃这一批 {} 条消息：{}", batch.size(), target.sessionKey());
            inbox.onTurnFinished();
        }
    }

    @PreDestroy
    public void shutdown() {
        debounceTimer.shutdownNow();
        turnPool.shutdownNow();
    }
}
