package io.github.mangomaner.mangobot.module.agent.chat;

import java.util.List;

/**
 * 每一轮发给 agent 的提示词的<b>唯一组装点</b>。
 *
 * <p>线格式（两块，控制块可缺）：
 * <pre>
 * [控制]
 * - 系统给你的指令
 * [/控制]
 *
 * [聊天记录]
 * [2026-09-27 10:00:00] 阿岩(1001): 今天面试
 * [2026-09-27 10:00:03] 小柯(1002): 绷不住了[表情]
 * </pre>
 *
 * <p><b>为什么必须只有一处拼</b>：这个项目已经吃过一次亏——信封格式原来在两个地方各写一遍，
 * 改了一处忘了另一处，于是"测评拿一种格式去衡量另一种格式的产物"。所以组装收成一个静态方法，
 * 谁都不许自己拼。加新的控制类型<b>不需要动这里</b>：控制是按条追加的（见 {@link TurnControlRegistry}）。
 *
 * <p><b>没有控制指令时原样返回信封</b>，不加任何包装：绝大多数轮次都属于这种，包一层既白花 token，
 * 又会让"格式变了"这件事凭空影响所有轮次的行为。
 *
 * <p>两块的语义差别要一直记着：控制是<b>给我方的指令</b>，聊天记录是<b>群里发生了什么</b>。
 * 系统提示词里明确要求"先执行控制块、且不要把控制块当成群友说的话"。
 */
public final class TurnPromptComposer {

    static final String CONTROL_OPEN = "[控制]";
    static final String CONTROL_CLOSE = "[/控制]";
    static final String RECORDS_OPEN = "[聊天记录]";

    private TurnPromptComposer() {
    }

    /**
     * 组装这一轮的提示词。
     *
     * @param controls 这一轮的控制指令，可为空；空则只发聊天记录
     * @param envelope 信封（一行一条聊天记录），由 {@code ChatTurnRunner} 拼好
     */
    public static String compose(List<TurnControl> controls, String envelope) {
        String records = envelope == null ? "" : envelope;
        if (controls == null || controls.isEmpty()) {
            return records;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(CONTROL_OPEN).append('\n');
        for (TurnControl control : controls) {
            // 一条一行，前缀 "- "：与信封的"一行一条"一致，模型已经习惯这个形状
            sb.append("- ").append(control.text()).append('\n');
        }
        sb.append(CONTROL_CLOSE).append('\n');
        if (!records.isBlank()) {
            sb.append('\n').append(RECORDS_OPEN).append('\n').append(records);
        }
        return sb.toString();
    }
}
