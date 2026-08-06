package io.github.mangomaner.mangobot.system.db;

import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

/**
 * SQLite 数据源配置
 *
 * <p>全新安装时按 schema.sql 建表并写入初始数据（数据库文件不存在时执行）。
 */
@Slf4j
@Configuration
public class SQLiteConfig {

    private static final String DATA_DIR = "data";
    private static final String DB_FILE = "mangobot.db";
    private static final String SCHEMA_SQL = "schema.sql";

    @Bean
    public DataSource dataSource() {
        Path dataDir = FileUtils.resolvePath(DATA_DIR);
        FileUtils.createDirectory(dataDir);
        Path dbPath = dataDir.resolve(DB_FILE);

        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        return DataSourceBuilder.create()
                .url(url)
                .driverClassName("org.sqlite.JDBC")
                .build();
    }

    @Bean
    public CommandLineRunner databaseInitializer(DataSource dataSource) {
        return args -> {
            Path dataDir = FileUtils.resolvePath(DATA_DIR);
            Path dbPath = dataDir.resolve(DB_FILE);

            if (!Files.exists(dbPath)) {
                initializeDatabase(dataSource);
            }
            // 存量库幂等迁移：schema.sql 仅在全新安装执行，这里补齐后续新增列
            migrateSchema(dataSource);
        };
    }

    /** 幂等增量迁移：对存量库补充缺失列（ALTER TABLE 在列已存在时会报错，需先探测） */
    private void migrateSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            ensureColumn(connection, "chat_session", "custom_prompt",
                    "ALTER TABLE chat_session ADD COLUMN custom_prompt TEXT");
            migrateOneBotConfigUrl(connection);
        } catch (Exception e) {
            log.warn("增量迁移失败", e);
        }
    }

    /** 存量 onebot_config 迁移：新增 url 列并从 host/port/path 回填，随后移除旧列 */
    private void migrateOneBotConfigUrl(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            boolean hasHost = columnExists(connection, "onebot_config", "host");
            if (hasHost) {
                if (!columnExists(connection, "onebot_config", "url")) {
                    statement.execute("ALTER TABLE onebot_config ADD COLUMN url TEXT");
                    statement.execute("UPDATE onebot_config SET url = 'ws://' || host || ':' || port || COALESCE(NULLIF(path, ''), '') WHERE url IS NULL OR url = ''");
                    log.info("增量迁移：onebot_config 增加列 url 并从 host/port/path 回填");
                }
                statement.execute("ALTER TABLE onebot_config DROP COLUMN host");
                statement.execute("ALTER TABLE onebot_config DROP COLUMN port");
                statement.execute("ALTER TABLE onebot_config DROP COLUMN path");
                log.info("增量迁移：onebot_config 移除旧列 host/port/path");
            }
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        try (Statement statement = connection.createStatement();
             var rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void ensureColumn(Connection connection, String table, String column, String alterSql) throws Exception {
        if (!columnExists(connection, table, column)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(alterSql);
                log.info("增量迁移：{} 增加列 {}", table, column);
            }
        }
    }

    private void initializeDatabase(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()
        ) {

            ClassPathResource resource = new ClassPathResource(SCHEMA_SQL);
            String sql = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);

            String[] statements = sql.split(";");
            for (String stmt : statements) {
                String trimmed = stmt.trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                    log.info("执行SQL: {}", trimmed);
                }
            }
            log.info("数据库初始化完成（schema.sql）");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }
}
