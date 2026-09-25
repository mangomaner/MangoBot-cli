package io.github.mangomaner.mangobot.module.agent.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个聊天（群 / 私聊）的收件箱：攒消息、防抖、开轮。
 *
 * <p>三态：{@code IDLE} 没人在说话 → 来一条进 {@code WINDOW}（防抖窗口）→ 窗口静默到期进
 * {@code RUNNING}（这一批已经交给轮次池了）。{@code RUNNING} 期间来的消息只进缓冲，
 * 等这一轮结束再看要不要立刻开下一轮——所以 bot 不会因为自己正在说话就把群里的话丢掉。
 *
 * <p>防抖是"尾部"的：窗口期每来一条就重置定时器。用"代次"(generation) 而不是取消 Future 来实现重置，
 * 这样定时任务只是个无状态的到点检查，过期代次直接无操作。
 *
 * <p>所有状态都在本对象的锁里改，方法由 {@code ChatInboxRegistry} 在派发线程与轮次线程两侧调用。
 */
final class ChatInbox {

    /** 缓冲上限：满了丢最旧的。防的是"轮次很慢 + 群里刷屏"把内存吃光。 */
    private static final int MAX_BUFFER = 50;

    private enum State {
        IDLE, WINDOW, RUNNING
    }

    private final ChatTarget target;
    private final ChatInboxRegistry owner;

    private final List<IncomingLine> buffer = new ArrayList<>();
    private State state = State.IDLE;
    private long generation = 0;

    ChatInbox(ChatTarget target, ChatInboxRegistry owner) {
        this.target = target;
        this.owner = owner;
    }

    ChatTarget target() {
        return target;
    }

    /** 收一条消息。派发线程上调用——只做入缓冲与排定时器，绝不做任何阻塞。 */
    synchronized void offer(IncomingLine line) {
        if (buffer.size() >= MAX_BUFFER) {
            buffer.remove(0);
        }
        buffer.add(line);

        if (state == State.RUNNING) {
            // 本轮还没结束，等 onTurnFinished 接手，别去动定时器
            return;
        }
        state = State.WINDOW;
        owner.scheduleDebounce(this, ++generation);
    }

    /**
     * 防抖到期检查；代次过期（窗口期又来了新消息）或状态已变则什么都不做。
     *
     * <p>由定时线程调用。
     */
    synchronized void fireIfWindow(long expectedGeneration) {
        if (state != State.WINDOW || generation != expectedGeneration) {
            return;
        }
        state = State.RUNNING;
        List<IncomingLine> batch = List.copyOf(buffer);
        buffer.clear();
        owner.startTurn(this, batch);
    }

    /** 一轮结束：还有缓冲就立刻开下一轮，否则回到空闲。由轮次线程调用。 */
    synchronized void onTurnFinished() {
        if (buffer.isEmpty()) {
            state = State.IDLE;
            return;
        }
        state = State.WINDOW;
        owner.scheduleDebounce(this, ++generation);
    }
}
