package io.github.mangomaner.mangobot.module.agent.capability.skill;

import io.github.mangomaner.mangobot.utils.FileUtils;

import java.nio.file.Path;

/**
 * 技能库的路径代数——技能在磁盘上的位置只在这里拼。
 *
 * <p>两个库，**类型由所在库决定**（数据库里没有类型标记位）：
 * <pre>
 * data/capabilities/
 * ├── all-skills/&lt;名&gt;/skills/&lt;名&gt;/SKILL.md      # 普通技能
 * └── all-personas/&lt;名&gt;/skills/&lt;名&gt;/SKILL.md    # 人格
 * </pre>
 *
 * <p>每层的 {@code <名>/} 是<b>插件根</b>：它的路径既是数据库里的 {@code skill_path}（前缀即类型，
 * 所以同名技能可以两个库各存一份），又是 {@code --plugin-dir} 的值本身——没有副本，
 * 下发时按作用域把若干个插件根拼进 argv。
 *
 * <p>为什么每个技能都要多套一层 {@code skills/<名>/}：claude 只认
 * {@code <插件根>/skills/<技能名>/SKILL.md} 这个形状，且<b>只扫一层</b>。裸技能目录
 * （{@code <根>/SKILL.md}）实测不被接受，再往下的目录则被静默忽略。这是形状的唯一原因，
 * 不是为了分类整理。
 */
public final class SkillLibrary {

    /** 普通技能库根（相对应用基准目录）。 */
    public static final String SKILLS_ROOT = "data/capabilities/all-skills";

    /** 人格库根。 */
    public static final String PERSONAS_ROOT = "data/capabilities/all-personas";

    /** 插件根下载技能的那层目录名，claude 的形状要求。 */
    public static final String SKILLS_SUBDIR = "skills";

    /** 技能类型。 */
    public enum Kind {
        /** 普通技能，只按来源（web / 群聊 / 私聊）启用。 */
        SKILL,
        /** 人格，按作用域（全局 / bot / 会话）指派。 */
        PERSONA
    }

    private SkillLibrary() {
    }

    public static String rootOf(Kind kind) {
        return kind == Kind.PERSONA ? PERSONAS_ROOT : SKILLS_ROOT;
    }

    /** 两个库的根，按固定顺序。 */
    public static String[] roots() {
        return new String[]{SKILLS_ROOT, PERSONAS_ROOT};
    }

    /** 技能名 → 库内路径（插件根），也就是存进数据库的 {@code skill_path}。 */
    public static String pathOf(Kind kind, String name) {
        return rootOf(kind) + "/" + name;
    }

    /** 由所在库判定类型；认不出的前缀一律按普通技能处理。 */
    public static Kind kindOf(String skillPath) {
        return skillPath != null && skillPath.startsWith(PERSONAS_ROOT + "/") ? Kind.PERSONA : Kind.SKILL;
    }

    /** 库内路径的最后一段：技能名，也是插件根目录名。 */
    public static String nameOf(String skillPath) {
        int slash = skillPath.lastIndexOf('/');
        return slash < 0 ? skillPath : skillPath.substring(slash + 1);
    }

    /** 插件根——{@code --plugin-dir} 的值。 */
    public static Path pluginRoot(String skillPath) {
        return FileUtils.resolvePath(skillPath);
    }

    /** 正文目录：{@code <插件根>/skills/<技能名>}，SKILL.md 与 references/ 都在这一层。 */
    public static Path contentDir(String skillPath) {
        return pluginRoot(skillPath).resolve(SKILLS_SUBDIR).resolve(nameOf(skillPath));
    }

    public static Path contentDirOf(Kind kind, String name) {
        return contentDir(pathOf(kind, name));
    }

    /** SKILL.md 的完整路径。 */
    public static Path skillMd(String skillPath) {
        return contentDir(skillPath).resolve("SKILL.md");
    }
}
