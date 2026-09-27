package io.github.mangomaner.mangobot.infra.claude.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能清单的映射表：作用域 → 一组技能目录；解析时 {@code SESSION → BOT → GLOBAL} 逐级回退。
 *
 * <p>infra 只存表与回退算法——不碰 DB、不读技能内容；表由调用方在技能库变更时填。
 *
 * <p>今天是"只有 GLOBAL 级"：每个来源（web/群聊/私聊）一份目录，所有 bot 共用。以后要支持
 * bot 级或会话级，只是多几行表项，解析代码不动。
 */
@Slf4j
@Component
public class ClaudeSkillRegistry {

    private final Map<ClaudeSkillScope, List<Path>> scopes = new ConcurrentHashMap<>();

    /** 覆盖某个作用域的清单。 */
    public void put(ClaudeSkillScope scope, List<Path> skillDirs) {
        scopes.put(scope, List.copyOf(skillDirs));
    }

    /**
     * 某个会话该下发哪些技能目录。
     *
     * <p>逐级回退是<b>取第一个有表项的</b>，不是合并——所以配了会话级就得自己列全想要的技能。
     *
     * @param source     来源标识；GLOBAL 级的范围 id 就是它
     * @param botId      可为空（探针 / judge 这类没有 bot 的会话）
     * @param sessionKey 会话键
     * @return 技能目录；三级都没有表项时为空，此时不传 {@code --plugin-dir}
     */
    public List<Path> resolve(String source, String botId, String sessionKey) {
        List<Path> dirs = scopes.get(new ClaudeSkillScope(ClaudeSkillScope.Level.SESSION, sessionKey));
        if (dirs != null) {
            return dirs;
        }
        if (botId != null) {
            dirs = scopes.get(new ClaudeSkillScope(ClaudeSkillScope.Level.BOT, botId));
            if (dirs != null) {
                return dirs;
            }
        }
        return scopes.getOrDefault(new ClaudeSkillScope(ClaudeSkillScope.Level.GLOBAL, source), List.of());
    }
}
