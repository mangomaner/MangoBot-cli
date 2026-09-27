package io.github.mangomaner.mangobot.module.agent.chat;

import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 打断策略：什么时候打断、一轮攒几条才打断、打断几次之后必须让它跑完。
 *
 * <p>这些规则在真实环境里要跑满一整轮 claude 才看得出一半，而错法的表现大多**静默**——
 * 比如静默判断写错导致每条消息都打断一次，预算一秒烧光，群里刷屏时 bot 再也不开口。
 * 所以拿假 host 单独钉住。
 */
class ChatInboxInterruptTest {

    /** 只记下被要求做了什么，不真开轮次。 */
    private static class FakeHost implements ChatInboxHost {
        final List<String> calls = new ArrayList<>();
        final List<Integer> batchSizes = new ArrayList<>();

        @Override
        public void scheduleDebounce(ChatInbox inbox, long generation) {
            calls.add("debounce");
        }

        @Override
        public void startTurn(ChatInbox inbox, List<IncomingLine> batch) {
            calls.add("start");
            batchSizes.add(batch.size());
        }

        @Override
        public void interruptTurn(ChatInbox inbox) {
            calls.add("interrupt");
        }

        long count(String what) {
            return calls.stream().filter(what::equals).count();
        }
    }

    private FakeHost host;
    private ChatInbox inbox;

    @BeforeEach
    void setUp() {
        host = new FakeHost();
        inbox = new ChatInbox(new ChatTarget(1L, SessionSource.GROUP, 2L), host);
    }

    private static IncomingLine line(long id) {
        return new IncomingLine(id, null, "m" + id, List.of());
    }

    /** 防抖到期。generation 就是 scheduleDebounce 被调用的次数。 */
    private void fireDebounce() {
        inbox.onDebounceFired(host.count("debounce"));
    }

    /** 直接进到"某一轮正在跑"：来一条、攒够、开轮。 */
    private void running() {
        inbox.offer(line(1));
        fireDebounce();
    }

    @Test
    @DisplayName("空闲时来的消息只排防抖，不打断")
    void idleDoesNotInterrupt() {
        inbox.offer(line(1));
        assertEquals(0, host.count("interrupt"), "没人在跑，打断没有意义");
    }

    /**
     * 这条是整个设计的核心：一轮在跑时来的几条消息，要**等它们也静默下来**才打断一次。
     * 每条消息都立刻打断的话，群里连发三条就把预算烧光了。
     */
    @Test
    @DisplayName("跑着时来的一批消息，静默满一秒才打断一次")
    void interruptsOncePerQuietBatch() {
        running();

        inbox.offer(line(2));
        inbox.offer(line(3));
        inbox.offer(line(4));
        assertEquals(0, host.count("interrupt"), "静默没到之前不该打断");

        fireDebounce();
        assertEquals(1, host.count("interrupt"), "三条消息只打断一次，不是三次");
    }

    @Test
    @DisplayName("连续打断到上限后就不再打断，让这一轮跑完")
    void stopsAfterBudget() {
        running();
        for (long i = 2; i < 10; i++) {
            inbox.offer(line(i));
            fireDebounce();
        }
        assertEquals(3, host.count("interrupt"), "上限是 3，之后必须让这一轮跑完");
    }

    @Test
    @DisplayName("被打断时攒的那批不丢，会成为下一轮的输入")
    void bufferedMessagesBecomeNextTurn() {
        running();
        inbox.offer(line(2));
        inbox.offer(line(3));
        fireDebounce();                       // 打断，buffer 不动

        inbox.onTurnFinished();               // 这一轮结束
        fireDebounce();                       // 下一轮开跑

        assertEquals(2, host.batchSizes.get(host.batchSizes.size() - 1).intValue(),
                "打断时攒的两条应该整批交给下一轮");
    }

    /**
     * 预算记的是"连续"：被打断的轮次结束后不清零，只有正常跑完才清零。
     * 记成"总共三次"的话，群里聊得久一点 bot 就永远不再打断、永远慢半拍。
     */
    @Test
    @DisplayName("被打断的那轮结束后预算不清零；正常跑完才清零")
    void budgetResetsOnlyAfterCleanTurn() {
        // 第 1 轮被打断一次后结束 —— 预算保持
        running();
        inbox.offer(line(2));
        fireDebounce();
        assertEquals(1, host.count("interrupt"));
        inbox.onTurnFinished();

        // 第 2 轮没人打断它，正常跑完 —— 预算清零
        fireDebounce();
        inbox.onTurnFinished();

        // 第 3 轮：因为清零过，又应该有完整的 3 次
        inbox.offer(line(3));
        fireDebounce();
        for (long i = 10; i < 20; i++) {
            inbox.offer(line(i));
            fireDebounce();
        }
        assertEquals(4, host.count("interrupt"), "第 2 轮正常跑完应清零，第 3 轮重新有 3 次");
    }

    @Test
    @DisplayName("缓冲攒下来的消息会在本轮结束后开下一轮")
    void bufferedMessagesStartNextTurn() {
        running();
        inbox.offer(line(2));
        long startsBefore = host.count("start");

        inbox.onTurnFinished();

        assertTrue(host.count("debounce") > 0, "结束后应重新排防抖");
        assertEquals(startsBefore, host.count("start"), "新的一轮要等静默到期才开，不是立刻开");
    }
}
