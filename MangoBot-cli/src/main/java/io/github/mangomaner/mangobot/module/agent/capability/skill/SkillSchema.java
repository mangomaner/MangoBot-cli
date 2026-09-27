package io.github.mangomaner.mangobot.module.agent.capability.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * {@code agent_skill_config} 的兜底补列与一次性数据迁移。
 *
 * <p>本项目没有迁移机制：{@code schema.sql} 只在 DB 文件不存在时执行，老库长不出新列，
 * 只能靠这里的 {@code ALTER TABLE}。和 {@link io.github.mangomaner.mangobot.module.file.service.FileSchema}
 * 是同一套办法，那边的注释里有更详细的来龙去脉。
 *
 * <h3>本次迁移（2026-09-28）：技能分双库 + 人格作用域三级化</h3>
 *
 * <p>技能类型从 {@code is_persona} 标记位改为<b>由所在库决定</b>（见 {@link SkillLibrary}），
 * 于是做了两件事：
 * <ol>
 *   <li>{@code skill_path} 加上库前缀（{@code catgirl} → {@code all-skills/catgirl}）。
 *       这样它既是身份（前缀即类型），又直接是 {@code --plugin-dir} 的值。</li>
 *   <li>消解人格的"多来源声称"：旧的指派只记来源、且取消时只摘一个源，于是常出现好几个人格
 *       同时声称同一个来源（谁生效取决于 SELECT 顺序）。新的模型是"每个作用域至多一个"，
 *       所以每个来源只保留<b>id 最小的那个声称者</b>——旧的 {@code activePersonaPathFor} 取的就是
 *       列表里第一个，顺序即 id，迁移后行为与迁移前一致。</li>
 * </ol>
 *
 * <p>幂等：路径已带库前缀就整段跳过，迁移不会跑第二次。
 */
@Component
@Slf4j
public class SkillSchema {

    /**
     * 人格标记位——<b>迁移读一次之后就不再使用</b>，新建库（{@code schema.sql}）也不再建这一列。
     *
     * <p>留着这条 {@code ALTER} 是因为迁移要读它：很老的库里没有这一列，不先补上，
     * 后面那条 {@code SELECT ... COALESCE(is_persona, 0)} 会直接报错。默认值给 0 而不是留空，
     * 于是"普通技能"在库里就是明确的 0，不用写双分支。
     */
    private static final String ADD_IS_PERSONA_SQL =
            "ALTER TABLE agent_skill_config ADD COLUMN is_persona BOOLEAN DEFAULT 0";

    private static final String SELECT_ALL_SQL =
            "SELECT id, skill_path, enabled_list, enabled, COALESCE(is_persona, 0) AS persona "
                    + "FROM agent_skill_config";

    private static final String UPDATE_SQL =
            "UPDATE agent_skill_config SET skill_path = ?, enabled_list = ?, enabled = ? WHERE id = ?";

    private final DataSource dataSource;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SkillSchema(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void init() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            addColumnQuietly(statement, ADD_IS_PERSONA_SQL);
        } catch (Exception e) {
            log.error("agent_skill_config 补列失败，技能功能将不可用", e);
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            migrateSkillPaths(connection);
        } catch (Exception e) {
            log.error("agent_skill_config 迁移失败；技能目录可能与数据库对不上，需人工核对", e);
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

    /** 一行旧数据。 */
    private record LegacyRow(int id, String skillPath, String enabledList, boolean enabled, boolean persona) {
    }

    private void migrateSkillPaths(Connection connection) throws Exception {
        List<LegacyRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SELECT_ALL_SQL)) {
            while (rs.next()) {
                rows.add(new LegacyRow(
                        rs.getInt("id"),
                        rs.getString("skill_path"),
                        rs.getString("enabled_list"),
                        rs.getBoolean("enabled"),
                        rs.getBoolean("persona")));
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        boolean anyLegacy = rows.stream().anyMatch(row -> !isLibraryPath(row.skillPath()));
        if (!anyLegacy) {
            return; // 已经迁移过
        }

        Map<String, Integer> winner = resolvePersonaWinners(rows);
        try (PreparedStatement update = connection.prepareStatement(UPDATE_SQL)) {
            for (LegacyRow row : rows) {
                String path = isLibraryPath(row.skillPath())
                        ? row.skillPath()
                        : SkillLibrary.pathOf(row.persona() ? SkillLibrary.Kind.PERSONA : SkillLibrary.Kind.SKILL,
                                row.skillPath());
                String enabledList = row.enabledList();
                boolean enabled = row.enabled();
                if (row.persona()) {
                    LinkedHashSet<String> kept = new LinkedHashSet<>();
                    if (row.enabled()) {
                        for (String key : parseKeys(row.enabledList())) {
                            if (winner.get(key) != null && winner.get(key) == row.id()) {
                                kept.add(key);
                            }
                        }
                    }
                    enabledList = objectMapper.writeValueAsString(kept);
                    enabled = !kept.isEmpty();
                }
                update.setString(1, path);
                update.setString(2, enabledList);
                update.setBoolean(3, enabled);
                update.setInt(4, row.id());
                update.executeUpdate();
            }
        }
        log.info("技能已迁移到双库布局（all-skills / all-personas）：{} 行；人格指派已按来源去重", rows.size());
    }

    /**
     * 每个来源的全局人格归谁：只在"启用了的、且声称该来源"的人格之间比，取 id 最小的那个
     * ——与旧 {@code activePersonaPathFor} 的"列表里第一个"一致。
     */
    private Map<String, Integer> resolvePersonaWinners(List<LegacyRow> rows) {
        Map<String, Integer> winner = new HashMap<>();
        rows.stream()
                .filter(row -> row.persona() && row.enabled())
                .sorted(Comparator.comparingInt(LegacyRow::id))
                .forEach(row -> parseKeys(row.enabledList()).stream()
                        .filter(SkillSchema::isSourceKey)
                        .forEach(key -> winner.putIfAbsent(key, row.id())));
        return winner;
    }

    private static boolean isLibraryPath(String skillPath) {
        if (skillPath == null) {
            return false;
        }
        for (String root : SkillLibrary.roots()) {
            if (skillPath.startsWith(root + "/")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSourceKey(String key) {
        for (SessionSource source : SessionSource.values()) {
            if (source.getSourceKey().equals(key)) {
                return true;
            }
        }
        return false;
    }

    private List<String> parseKeys(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("技能启用列表不是合法 JSON，按空处理：{}", json);
            return List.of();
        }
    }
}
