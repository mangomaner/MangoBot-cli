package io.github.mangomaner.mangobot.module.agent.sim.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 模拟台两张表的兜底建表。
 *
 * <p>本项目没有迁移机制：{@code schema.sql} 只在 DB 文件不存在时执行，老库长不出新表，
 * 只能靠这里的 {@code CREATE TABLE IF NOT EXISTS}。所以这里的定义与 {@code schema.sql} 里那两份
 * <b>必须逐字一致</b>，改一处要改两处。
 */
@Component
@Slf4j
public class SimSchema {

    private static final String CREATE_SESSION_SQL = """
            CREATE TABLE IF NOT EXISTS sim_session (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                title         TEXT,
                source        TEXT NOT NULL,
                bot_id        TEXT NOT NULL,
                chat_id       TEXT NOT NULL,
                persona_label TEXT,
                seed_ref      TEXT,
                status        TEXT NOT NULL,
                scores        TEXT,
                verdict       TEXT,
                create_time   DATETIME DEFAULT CURRENT_TIMESTAMP
            )""";

    private static final String CREATE_MESSAGE_SQL = """
            CREATE TABLE IF NOT EXISTS sim_message (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id  INTEGER NOT NULL,
                seq         INTEGER NOT NULL,
                speaker     TEXT,
                sender_id   TEXT,
                role        TEXT NOT NULL,
                kind        TEXT NOT NULL,
                content     TEXT,
                reply_to    TEXT,
                at_ms       INTEGER,
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP
            )""";

    private static final String CREATE_MESSAGE_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS idx_sim_message_session ON sim_message (session_id, seq)";

    /**
     * 给已经存在的老表补列。
     *
     * <p>SQLite 没有 {@code ADD COLUMN IF NOT EXISTS}，重复加会报 duplicate column name——
     * 那就正是"已经有了"，吞掉即可。这张表是本轮新建的，所以现在还只有一条补列记录；
     * 留这个机制是因为库是长期存在的，而 {@code CREATE TABLE IF NOT EXISTS} 对已存在的表什么都不做。
     */
    private static final String ADD_REPLY_TO_SQL =
            "ALTER TABLE sim_message ADD COLUMN reply_to TEXT";

    private final DataSource dataSource;

    public SimSchema(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void init() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_SESSION_SQL);
            statement.execute(CREATE_MESSAGE_SQL);
            statement.execute(CREATE_MESSAGE_INDEX_SQL);
            addColumnQuietly(statement, ADD_REPLY_TO_SQL);
            log.info("sim_session / sim_message 表就绪");
        } catch (Exception e) {
            log.error("创建模拟台的表失败，模拟对话将无法入库", e);
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
