package io.github.mangomaner.mangobot.infra.claude.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能清单的映射表：作用域 → 一组插件根目录；解析时 {@code SESSION → BOT → GLOBAL} 逐级回退。
 *
 * <p>表里存的是<b>每个技能自己的插件根</b>（{@code <技能根>/skills/<技能名>/} 的上一层），
 * 一张表项可能有好几个目录，展开就是多个 {@code --plugin-dir}——所以没有"按作用域复制目录"这回事。
 *
 * <p>infra 只存表与回退算法——不碰 DB、不读技能内容；表由调用方在技能库变更时填。
 * 整表替换是<b>原子</b>的（持有不可变快照、换引用），所以重建期间拉起的新会话要么拿到旧表、
 * 要么拿到新表，不会拿到半张。
 */
@Slf4j
@Component
public class ClaudeSkillRegistry {

    /** 不可变快照，换引用即整表替换。 */
    private volatile Map<ClaudeSkillScope, List<Path>> scopes = Map.of();

    /** 整表替换。调用方把全部作用域一次算好，别一条条 put——逐条替换清不掉已删条目的旧表项。 */
    public void replaceAll(Map<ClaudeSkillScope, List<Path>> table) {
        Map<ClaudeSkillScope, List<Path>> copy = new HashMap<>();
        table.forEach((scope, dirs) -> copy.put(scope, List.copyOf(dirs)));
        this.scopes = Map.copyOf(copy);
    }

    /** 当前表里的作用域数量，供启动日志与诊断用。 */
    public int size() {
        return scopes.size();
    }

    /**
     * 某个会话该下发哪些技能目录。
     *
     * <p>逐级回退是<b>取第一个有表项的</b>，不是合并——所以配了会话级就得自己列全想要的技能，
     * 这也是调用方在写表时就必须把"该级能看到的全部技能"算进去的原因。
     *
     * @param source     来源标识；GLOBAL 级的范围 id 就是它
     * @param botId      可为空（探针 / judge 这类没有 bot 的会话）
     * @param sessionKey 会话键
     * @return 插件根目录；三级都没有表项时为空，此时不传 {@code --plugin-dir}
     */
    public List<Path> resolve(String source, String botId, String sessionKey) {
        Map<ClaudeSkillScope, List<Path>> snapshot = scopes;
        List<Path> dirs = snapshot.get(ClaudeSkillScope.session(sessionKey));
        if (dirs != null) {
            return dirs;
        }
        if (botId != null) {
            dirs = snapshot.get(ClaudeSkillScope.bot(botId, source));
            if (dirs != null) {
                return dirs;
            }
        }
        return snapshot.getOrDefault(ClaudeSkillScope.global(source), List.of());
    }
}
