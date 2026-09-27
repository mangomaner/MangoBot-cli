package io.github.mangomaner.mangobot.infra.claude.skill;

/**
 * 技能作用域：级别 + 范围 id。
 *
 * <p>范围 id 对 infra <b>不透明</b>——它只是查表的键，含义（哪个 bot、哪个会话）由调用方定。
 * 但拼法要唯一，所以给三个静态工厂：写表的一侧与解析的一侧都用它们，别各自拼字符串。
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

    /** 全局级：范围 id 就是来源标识（{@code web} / {@code group} / {@code private}）。 */
    public static ClaudeSkillScope global(String sourceKey) {
        return new ClaudeSkillScope(Level.GLOBAL, sourceKey);
    }

    /** bot 级：一个来源下一个 bot，故 id 要带来源，免得两个来源的同号 bot 撞成一条。 */
    public static ClaudeSkillScope bot(String botId, String sourceKey) {
        return new ClaudeSkillScope(Level.BOT, botId + ":" + sourceKey);
    }

    /**
     * 会话级：范围 id 就是 claude 会话键本身（{@code 1389:group:67890} / {@code web:<会话 id>}），
     * 它已经全局唯一，不用再拼来源。
     */
    public static ClaudeSkillScope session(String sessionKey) {
        return new ClaudeSkillScope(Level.SESSION, sessionKey);
    }
}
