package io.github.mangomaner.mangobot.module.agent.chat;

/**
 * 一条"控制指令"：由我方（而不是群友）发给 agent，用来在会话进行中持续影响它的行为。
 *
 * <p>它和信封走同一条路（同一个 user 消息），但语义完全不同：信封是"群里发生了什么"，
 * 控制是"你要怎么做"。所以两者在 {@link TurnPromptComposer} 里被分块渲染，并且系统提示词里
 * 明确要求先执行控制块。
 *
 * <p>典型用途：技能正文更新后要求重读、切换形态、临时加强/放开某条约束。
 *
 * @param id   标识，用于覆盖与撤销（{@link TurnControlRegistry#pin} / {@link #unpin}）以及排查时对号；
 *             不渲染进提示词——模型只需要正文
 * @param text 给模型看的那句话，用陈述/祈使句写清楚要它做什么
 */
public record TurnControl(String id, String text) {

    public TurnControl {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("控制指令必须有 id（用于覆盖与撤销）");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("控制指令不能为空");
        }
    }
}
