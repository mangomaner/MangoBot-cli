package io.github.mangomaner.mangobot.module.file.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * {@code bot_files} 的兜底补列。
 *
 * <p>本项目没有迁移机制：{@code schema.sql} 只在 DB 文件不存在时执行，老库长不出新列，
 * 只能靠这里的 {@code ALTER TABLE}。和 {@link io.github.mangomaner.mangobot.module.agent.sim.service.SimSchema}
 * 是同一套办法，那边的注释里有更详细的来龙去脉。
 *
 * <p>{@code bot_files} 是张有上万行的老表，所以补列必须是**加列**而不是重建：
 * SQLite 的 {@code ADD COLUMN} 带默认值只改元数据、不重写表，对存量数据是安全的。
 */
@Component
@Slf4j
public class FileSchema {

    /**
     * 表情收藏标记。
     *
     * <p>默认值给 0 而不是留空：存量行补列时会统一填 0，于是「没收藏」在数据库里就是明确的 0，
     * 查询端不用再写 {@code favorite IS NULL OR favorite = 0} 那种双分支。
     *
     * <p><b>这条不会把任何已有表情变成收藏</b>——收藏是人工在表情管理页里一个个挑的，
     * 迁移不预置。所以在人去收藏第一批之前，发表情的能力是关着的。
     */
    private static final String ADD_FAVORITE_SQL =
            "ALTER TABLE bot_files ADD COLUMN favorite INTEGER DEFAULT 0";

    private final DataSource dataSource;

    public FileSchema(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void init() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            addColumnQuietly(statement, ADD_FAVORITE_SQL);
        } catch (Exception e) {
            log.error("bot_files 补列失败，表情收藏功能将不可用", e);
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
