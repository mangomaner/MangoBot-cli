package io.github.mangomaner.mangobot.module.agent.capability.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * {@code agent_skill_config} 的兜底补列。
 *
 * <p>本项目没有迁移机制：{@code schema.sql} 只在 DB 文件不存在时执行，老库长不出新列，
 * 只能靠这里的 {@code ALTER TABLE}。和 {@link io.github.mangomaner.mangobot.module.file.service.FileSchema}
 * 是同一套办法，那边的注释里有更详细的来龙去脉。
 *
 * <p>{@code agent_skill_config} 是张长期存在的表，所以补列必须是**加列**而不是重建：
 * SQLite 的 {@code ADD COLUMN} 带默认值只改元数据、不重写表，对存量数据是安全的。
 */
@Component
@Slf4j
public class SkillSchema {

    /**
     * 人格 skill 标记。
     *
     * <p>人格 skill 与普通 skill 的区分由**导入界面**决定：在「技能」页导入就是普通技能，
     * 在「人格」页导入就是人格技能（不靠解析 SKILL.md 的 frontmatter，避免同名文件被误判）。
     *
     * <p>默认值给 0 而不是留空：存量行补列时统一填 0，于是「普通技能」在数据库里就是明确的 0，
     * 查询端不用再写 {@code is_persona IS NULL OR is_persona = 0} 那种双分支。
     *
     * <p><b>这条不会把任何已有技能变成人格</b>——迁移不预置。所以在用户通过人格页导入第一批
     * 之前，人格页是空的。
     */
    private static final String ADD_IS_PERSONA_SQL =
            "ALTER TABLE agent_skill_config ADD COLUMN is_persona BOOLEAN DEFAULT 0";

    private final DataSource dataSource;

    public SkillSchema(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void init() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            addColumnQuietly(statement, ADD_IS_PERSONA_SQL);
        } catch (Exception e) {
            log.error("agent_skill_config 补列失败，人格技能功能将不可用", e);
        }
    }

    /** 加列；列已存在时报的 duplicate column name 不是错误，其余照旧抛。 */
    private static void addColumnQuietly(Statement statement, String sql) throws Exception {
        try {
            statement.execute(sql);
            log.info("已补列：{}", sql);
        } catch (Exception e) {
            if (String.valueOf(e.getMessage()).contains("duplicate column name")) {
                return;
            }
            throw e;
        }
    }
}