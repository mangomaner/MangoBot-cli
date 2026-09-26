package io.github.mangomaner.mangobot.module.agent.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 控制指令登记处的语义。
 *
 * <p>这里钉住三件容易写错的事：peek 不能消费（本轮提交不出去时控制不能丢）、提交成功才清一次性、
 * 常驻同 id 覆盖（"切换形态"就是换个同 id 的值，不是堆一堆）。
 */
class TurnControlRegistryTest {

    private final TurnControlRegistry registry = new TurnControlRegistry();
    private static final String KEY = "3970521445:group:420418118";

    @Test
    @DisplayName("peek 只读：连看两次都还在，不清空")
    void peekDoesNotConsume() {
        registry.appendOnce(KEY, new TurnControl("a", "先做 A"));

        assertEquals(1, registry.peek(KEY).size());
        assertEquals(1, registry.peek(KEY).size(), "peek 把排队的控制吃掉了——本轮提交失败就丢了");
    }

    @Test
    @DisplayName("提交成功后一次性被清掉，常驻留着继续带")
    void commitClearsOnceOnly() {
        registry.appendOnce(KEY, new TurnControl("once-1", "一次性的"));
        registry.pin(KEY, new TurnControl("mode", "常驻的"));

        assertEquals(2, registry.peek(KEY).size());
        registry.commitOnce(KEY);

        List<TurnControl> next = registry.peek(KEY);
        assertEquals(1, next.size(), "一次性没清掉，或常驻被误清");
        assertEquals("mode", next.get(0).id());
    }

    @Test
    @DisplayName("次序：一次性在前，常驻在后")
    void onceComesBeforePinned() {
        registry.pin(KEY, new TurnControl("mode", "常驻"));
        registry.appendOnce(KEY, new TurnControl("once", "一次性"));

        List<TurnControl> got = registry.peek(KEY);
        assertEquals("once", got.get(0).id());
        assertEquals("mode", got.get(1).id());
    }

    @Test
    @DisplayName("常驻同 id 覆盖：切形态不该堆成一串")
    void pinReplacesSameId() {
        registry.pin(KEY, new TurnControl("mode", "克制一点"));
        registry.pin(KEY, new TurnControl("mode", "放开一点"));

        List<TurnControl> got = registry.peek(KEY);
        assertEquals(1, got.size());
        assertEquals("放开一点", got.get(0).text());
    }

    @Test
    @DisplayName("unpin 撤掉常驻；clear 连一次性一起清")
    void unpinAndClear() {
        registry.pin(KEY, new TurnControl("mode", "常驻"));
        registry.appendOnce(KEY, new TurnControl("once", "一次性"));

        registry.unpin(KEY, "mode");
        assertEquals(1, registry.peek(KEY).size());

        registry.clear(KEY);
        assertTrue(registry.peek(KEY).isEmpty());
    }

    @Test
    @DisplayName("会话之间互不串味")
    void isolatedPerSession() {
        registry.appendOnce(KEY, new TurnControl("a", "给这个群的"));
        assertTrue(registry.peek("3970521445:private:2756477287").isEmpty());
    }

    @Test
    @DisplayName("空 id / 空正文直接拒绝——不然撤销和排查都无从下手")
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new TurnControl("", "有正文没 id"));
        assertThrows(IllegalArgumentException.class, () -> new TurnControl("id", "  "));
    }
}
