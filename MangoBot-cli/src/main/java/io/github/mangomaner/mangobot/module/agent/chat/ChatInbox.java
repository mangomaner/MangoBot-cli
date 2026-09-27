package io.github.mangomaner.mangobot.module.agent.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个聊天（群 / 私聊）的收件箱：攒消息、防抖、开轮、打断。
 *
 * <h3>四态</h3>
 * <pre>
 *   IDLE      空闲
 *   WINDOW    攒第一批：静默满 1 秒就开一轮
 *   RUNNING   一轮在跑，暂时没什么要做的
 *   STIRRING  一轮在跑，但已经攒了新消息：静默满 1 秒就去打断它，用新的这批重开
 * </pre>
 *
 * <p>{@code WINDOW} 和 {@code STIRRING} 是同一套防抖机制的两个用途，分开是因为到期后做的事
 * 完全不同（一个开轮、一个打断）——合并成一个状态就得再存一个"到期干什么"的标记，反而更绕。
 *
 * <h3>为什么要打断</h3>
 * 一轮的输入在开跑那一刻就固定了。群里还在继续说的时候，它手上那份快照很快就过期，
 * 说出来的话往往是回答几句之前的问题。所以攒一批新的、静默下来之后把它掐掉重开，
 * 让 bot 基于最新的上下文说话，而不是把过时的话说完。
 *
 * <h3>防抖与预算</h3>
 * 防抖是"尾部"的：窗口期每来一条就重置定时器。用"代次"(generation) 而不是取消 Future 来实现重置，
 * 这样定时任务只是个无状态的到点检查，过期代次直接无操作。
 *
 * <p>但打断不能无限来——群里一直刷屏的话，这一轮永远跑不完，bot 一句话都说不出来。
 * 所以连续打断 {@value #MAX_CONSECUTIVE_INTERRUPTS} 次之后就不再打断，让这一轮跑完；
 * **正常跑完一次才清零**（见 {@link #onTurnFinished()}），因为要限的是"连续"。
 *
 * <p>所有状态都在本对象的锁里改，方法由 {@code ChatInboxRegistry} 在派发线程与轮次线程两侧调用。
 */
final class ChatInbox {

    /** 缓冲上限：满了丢最旧的。防的是"轮次很慢 + 群里刷屏"把内存吃光。 */
    private static final int MAX_BUFFER = 50;

    /** 连续打断几次之后必须让这一轮跑完。 */
    private static final int MAX_CONSECUTIVE_INTERRUPTS = 3;

    private enum State {
        IDLE, WINDOW, RUNNING, STIRRING
    }

    private final ChatTarget target;
    private final ChatInboxHost owner;

    private final List<IncomingLine> buffer = new ArrayList<>();
    private State state = State.IDLE;
    private long generation = 0;

    /** 连续打断已经用掉几次。跑完一轮正常的就清零。 */
    private int interruptsUsed = 0;

    /** 刚结束的这一轮是不是被我们打断的——用来区分"跑完了"和"又被打断了"。 */
    private boolean currentTurnInterrupted = false;

    ChatInbox(ChatTarget target, ChatInboxHost owner) {
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

        if (state == State.RUNNING || state == State.STIRRING) {
            // 一轮在跑：不是立刻打断，而是等这批也静默下来再去打断，
            // 这样一次能带上完整的一批，预算也不会被连续几条消息瞬间烧光。
            if (interruptsUsed < MAX_CONSECUTIVE_INTERRUPTS) {
                state = State.STIRRING;
                owner.scheduleDebounce(this, ++generation);
            }
            // 预算用尽：只攒着，等这一轮跑完当下一批（见 onTurnFinished）
            return;
        }
        state = State.WINDOW;
        owner.scheduleDebounce(this, ++generation);
    }

    /**
     * 防抖到期。代次过期（窗口期又来了消息）就什么都不做。由定时线程调用。
     *
     * <p>到期后做什么由当时的状态决定：{@link State#WINDOW} 是开一轮，{@link State#STIRRING}
     * 是把正在跑的那轮掐掉重来。
     */
    synchronized void onDebounceFired(long expectedGeneration) {
        if (generation != expectedGeneration) {
            return;
        }
        switch (state) {
            case WINDOW -> startTurn();
            case STIRRING -> interruptRunningTurn();
            default -> {
            }
        }
    }

    private void startTurn() {
        state = State.RUNNING;
        currentTurnInterrupted = false;
        List<IncomingLine> batch = List.copyOf(buffer);
        buffer.clear();
        owner.startTurn(this, batch);
    }

    private void interruptRunningTurn() {
        state = State.RUNNING;
        interruptsUsed++;
        currentTurnInterrupted = true;
        owner.interruptTurn(this);
    }

    /**
     * 一轮结束：还有缓冲就攒下一轮，否则回到空闲。由轮次线程调用。
     *
     * <p>打断预算按"连续"算：只有**正常跑完**才清零。被打断结束的那一轮不清——否则
     * "连续 3 次"这条上限永远也攒不满。
     */
    synchronized void onTurnFinished() {
        if (!currentTurnInterrupted) {
            interruptsUsed = 0;
        }
        currentTurnInterrupted = false;

        if (buffer.isEmpty()) {
            state = State.IDLE;
            return;
        }
        state = State.WINDOW;
        owner.scheduleDebounce(this, ++generation);
    }
}
