package io.github.mangomaner.mangobot.infra.claude.skill;

/**
 * 技能作用域：级别 + 范围 id。
 *
 * <p>范围 id 对 infra <b>不透明</b>——它只是查表的键，含义（哪个 bot、哪个会话）由调用方定。
 */
public record ClaudeSkillScope(Level level, String scopeId) {

    /** 从具体到笼统；解析按这个顺序回退。 */
    public enum Level {
        // 单个会话
        SESSION,
        // 一个 bot
        BOT,
        // 全局；调用方按来源（web / 群聊 / 私聊）各放一份
        GLOBAL
    }
}
