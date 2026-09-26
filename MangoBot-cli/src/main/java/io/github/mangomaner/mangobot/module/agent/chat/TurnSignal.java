package io.github.mangomaner.mangobot.module.agent.chat;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一个会话"这一轮真的跑完了"的信号。
 *
 * <p>生产链路在一轮结束时喊一声；只有模拟台会来等它。没人登记的会话键上所有操作都是空操作，
 * 所以对生产零影响——这条性质是它能放在这里的前提。
 *
 * <p>为什么需要它：模拟对话要按轮推进（"这一批发出去，等 bot 说完，再发下一批"），
 * 就必须知道一轮何时结束。早发会被防抖并进上一轮，晚发只是浪费墙钟。
 *
 * <p>为什么喊话点在 {@link ChatInboxRegistry} 而不是 {@link ChatTurnRunner} 的 finally：
 * 后者在"取会话失败"时会提前 return、把整个 finally 跳过（会话没了就没人喊），
 * 而 {@code ChatInboxRegistry} 那层是"把这一批交给轮次池"的收尾，无论轮次内部怎么结束都会走到，
 * 且每个会话每轮恰好一次。
 *
 * <p>为什么是静态类而不是 Spring bean：喊话方在轮次编排那侧（拿得到注入，但不想为一条诊断信号
 * 引入依赖环），等待方要按会话键对号入座，两边唯一的共同点就是这个类加载器里的静态入口。
 */
public final class TurnSignal {

    private static final Map<String, Turn> PENDING = new ConcurrentHashMap<>();

    private TurnSignal() {
    }

    /**
     * 宣布"马上会有一轮，等它结束"。必须在发这一批消息<b>之前</b>调用。
     *
     * <p>同时清掉上一轮留下的正文——不这样的话，一轮"取会话失败"的早退会把上一轮的模型正文
     * 当成这一轮的，观测数据就串了。
     */
    public static void expect(String sessionKey) {
        PENDING.put(sessionKey, new Turn(new CountDownLatch(1), new AtomicReference<>()));
    }

    /** 记下这一轮模型直接吐在正文里的字（群聊里它本该调工具，非空是要看的信号）。 */
    public static void noteAssistantText(String sessionKey, String text) {
        Turn turn = PENDING.get(sessionKey);
        if (turn != null) {
            turn.assistantText().set(text == null ? "" : text);
        }
    }

    /** 一轮结束。轮次线程上调用；没有人在等这个会话时是空操作。 */
    public static void finished(String sessionKey) {
        Turn turn = PENDING.get(sessionKey);
        if (turn != null) {
            turn.latch().countDown();
        }
    }

    /**
     * 等这一轮结束。
     *
     * @return 这一轮模型的正文（可能是空串）；<b>超时或没人登记时为空</b>——超时是"消息压根没进链路"
     *         （白名单没播种 / 服务端没起来）的典型症状，调用方必须把它当失败报出来，不能当"这轮沉默"
     */
    public static Optional<String> awaitTurn(String sessionKey, long timeoutMillis) throws InterruptedException {
        Turn turn = PENDING.get(sessionKey);
        if (turn == null) {
            return Optional.empty();
        }
        if (!turn.latch().await(timeoutMillis, TimeUnit.MILLISECONDS)) {
            return Optional.empty();
        }
        return Optional.of(turn.assistantText().get() == null ? "" : turn.assistantText().get());
    }

    /** 模拟收尾时撤掉登记；之后这个会话键的信号彻底变回空操作。 */
    public static void clear(String sessionKey) {
        PENDING.remove(sessionKey);
    }

    private record Turn(CountDownLatch latch, AtomicReference<String> assistantText) {
    }
}
