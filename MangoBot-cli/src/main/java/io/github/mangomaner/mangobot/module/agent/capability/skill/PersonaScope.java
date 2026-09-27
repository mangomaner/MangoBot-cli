package io.github.mangomaner.mangobot.module.agent.capability.skill;

import io.github.mangomaner.mangobot.infra.claude.skill.ClaudeSkillScope;
import io.github.mangomaner.mangobot.module.agent.chat.SessionKeys;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;

import java.util.regex.Pattern;

/**
 * 人格指派的作用域：来源 + 可选的 bot + 可选的会话。
 *
 * <p>三级的键格式（存进 {@code agent_skill_config.enabled_list} 的就是它）：
 * <pre>
 * 全局   group                    ← 就是一个来源键
 * bot    1389:group
 * 会话   1389:group:67890         ← 与 claude 会话键是同一个字符串
 * </pre>
 * 会话键直接由 {@link SessionKeys} 拼，bot 键是会话键去掉目标段——两边共用一套拼法，
 * 免得"注册表里的键"和"会话键"各写一份、将来某一侧改了另一侧静默失配。
 *
 * <p>web 只有全局级：它跑 claude 时的 botId 是常量 {@code web}，没有更细的槽位。
 * botId / targetId 限定为纯数字（QQ 号），非数字一律拒绝。
 */
public record PersonaScope(SessionSource source, String botId, String targetId) {

    private static final Pattern NUMERIC_ID = Pattern.compile("\\d{1,20}");

    public enum Level {
        /** 一个来源下的全部会话。 */
        GLOBAL,
        /** 一个 bot 在一个来源下的全部会话。 */
        BOT,
        /** 单个会话。 */
        SESSION
    }

    public PersonaScope {
        if (source == null) {
            throw new IllegalArgumentException("作用域缺少来源");
        }
        if (targetId != null && botId == null) {
            throw new IllegalArgumentException("会话级作用域必须带 botId");
        }
        if (source == SessionSource.WEB && (botId != null || targetId != null)) {
            throw new IllegalArgumentException("Web 只支持全局人格，没有 bot / 会话级");
        }
        if (botId != null && !NUMERIC_ID.matcher(botId).matches()) {
            throw new IllegalArgumentException("botId 必须是数字: " + botId);
        }
        if (targetId != null && !NUMERIC_ID.matcher(targetId).matches()) {
            throw new IllegalArgumentException("targetId 必须是数字: " + targetId);
        }
    }

    public static PersonaScope global(SessionSource source) {
        return new PersonaScope(source, null, null);
    }

    public static PersonaScope bot(SessionSource source, String botId) {
        return new PersonaScope(source, botId, null);
    }

    public static PersonaScope session(SessionSource source, String botId, String targetId) {
        return new PersonaScope(source, botId, targetId);
    }

    /** 反解作用域键；格式不合法抛 {@link IllegalArgumentException}。 */
    public static PersonaScope parse(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("作用域键为空");
        }
        String[] parts = key.trim().split(":");
        try {
            return switch (parts.length) {
                case 1 -> global(sourceOf(parts[0]));
                case 2 -> bot(sourceOf(parts[1]), parts[0]);
                case 3 -> session(sourceOf(parts[1]), parts[0], parts[2]);
                default -> throw new IllegalArgumentException("无法解析的作用域键: " + key);
            };
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无法解析的作用域键: " + key);
        }
    }

    public Level level() {
        if (targetId != null) {
            return Level.SESSION;
        }
        return botId != null ? Level.BOT : Level.GLOBAL;
    }

    /** 存进 {@code enabled_list} 的键。 */
    public String key() {
        return switch (level()) {
            case GLOBAL -> source.getSourceKey();
            case BOT -> botId + ":" + source.getSourceKey();
            case SESSION -> SessionKeys.qq(Long.parseLong(botId), source, Long.parseLong(targetId));
        };
    }

    /** 对应的技能清单表项键。 */
    public ClaudeSkillScope toSkillScope() {
        return switch (level()) {
            case GLOBAL -> ClaudeSkillScope.global(source.getSourceKey());
            case BOT -> ClaudeSkillScope.bot(botId, source.getSourceKey());
            case SESSION -> ClaudeSkillScope.session(key());
        };
    }

    /** 给人看的说法，进日志与控制指令。 */
    public String display() {
        return switch (level()) {
            case GLOBAL -> source.getDescription();
            case BOT -> source.getDescription() + "（bot " + botId + "）";
            case SESSION -> source.getDescription() + "（bot " + botId + " · 会话 " + targetId + "）";
        };
    }

    private static SessionSource sourceOf(String sourceKey) {
        return SessionSource.fromKey(sourceKey);
    }
}
