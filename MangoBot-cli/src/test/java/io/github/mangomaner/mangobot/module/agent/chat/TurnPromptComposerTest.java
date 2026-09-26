package io.github.mangomaner.mangobot.module.agent.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 每轮提示词的线格式。
 *
 * <p>它只有一处组装（{@link TurnPromptComposer}），所以格式改坏了要立刻红。这里钉的是两条：
 * 控制块与聊天记录分块、且**没有控制指令时原样返回信封**（绝大多数轮次走这条路，不该被包装动到）。
 */
class TurnPromptComposerTest {

    private static final String ENVELOPE =
            "[2026-09-27 10:00:00] 阿岩(1001): 今天面试\n[2026-09-27 10:00:03] 小柯(1002): 绷不住了[表情]";

    @Test
    @DisplayName("没有控制指令：原样返回信封，不加任何包装")
    void noControlsMeansPlainEnvelope() {
        assertEquals(ENVELOPE, TurnPromptComposer.compose(List.of(), ENVELOPE));
        assertEquals(ENVELOPE, TurnPromptComposer.compose(null, ENVELOPE));
    }

    @Test
    @DisplayName("有控制指令：控制块在前、聊天记录在后，中间空一行")
    void rendersBothBlocks() {
        String out = TurnPromptComposer.compose(
                List.of(new TurnControl("skill:x", "技能 x 更新了，重新读一遍。")), ENVELOPE);

        assertEquals("[控制]\n"
                + "- 技能 x 更新了，重新读一遍。\n"
                + "[/控制]\n"
                + "\n"
                + "[聊天记录]\n"
                + ENVELOPE, out);
    }

    @Test
    @DisplayName("多条控制各占一行，且 id 不外泄进提示词")
    void multipleControlsOneLineEach() {
        String out = TurnPromptComposer.compose(List.of(
                new TurnControl("a", "第一件事"),
                new TurnControl("b", "第二件事")), ENVELOPE);

        assertTrue(out.contains("- 第一件事\n- 第二件事\n"), out);
        assertFalse(out.contains("a"), "控制指令的 id 不该渲染给模型");
        assertEquals(1, out.split("\\[控制\\]", -1).length - 1);
        assertEquals(1, out.split("\\[/控制\\]", -1).length - 1);
    }

    @Test
    @DisplayName("信封为空（只发控制）时不写空的聊天记录块")
    void controlOnlyTurnOmitsRecordsBlock() {
        String out = TurnPromptComposer.compose(
                List.of(new TurnControl("a", "只发指令")), "");

        assertEquals("[控制]\n- 只发指令\n[/控制]\n", out);
        assertFalse(out.contains("[聊天记录]"));
    }
}
