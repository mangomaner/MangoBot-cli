package io.github.mangomaner.mangobot.system.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.module.agent.core.AgentCompactionConfig;
import io.github.mangomaner.mangobot.module.configuration.model.config.ConfigMetadata;
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
import java.sql.PreparedStatement;
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
            // 类型化默认配置播种：schema.sql 仅建表与静态数据，动态默认值在此补齐
            seedDefaultBotConfigs(dataSource);
        };
    }

    /** 幂等增量迁移：对存量库补充缺失列（ALTER TABLE 在列已存在时会报错，需先探测） */
    private void migrateSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
        } catch (Exception e) {
            log.warn("增量迁移失败", e);
        }
    }

    /**
     * 播种默认 Bot 配置（幂等，全新库与存量库均执行）。
     *
     * <p>默认值与 metadata.schema 由类型化模型序列化（单一事实来源），避免在
     * schema.sql 硬编码 JSON 字面量导致两处漂移；存量库同样由本方法补齐缺失行。
     */
    private void seedDefaultBotConfigs(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            seedAgentCompactionConfig(connection);
        } catch (Exception e) {
            log.warn("播种默认 Bot 配置失败", e);
        }
    }

    /** 播种 agent.compaction 默认配置（幂等）：用类型化模型序列化默认值与 schema。 */
    private void seedAgentCompactionConfig(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             var rs = statement.executeQuery(
                     "SELECT COUNT(*) FROM bot_configs WHERE config_key = 'agent.compaction' AND bot_id IS NULL")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }

        ObjectMapper mapper = new ObjectMapper();
        ConfigMetadata metadata = new ConfigMetadata();
        metadata.setSchema(AgentCompactionConfig.schema());
        String configValue = mapper.writeValueAsString(AgentCompactionConfig.defaults());
        String metadataJson = mapper.writeValueAsString(metadata);

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO bot_configs (bot_id, config_key, config_value, config_type, metadata, description, explain, category, editable) "
                        + "VALUES (NULL, 'agent.compaction', ?, 'JSON', ?, '上下文压缩阈值', "
                        + "'按 web/group/private 三端分别配置压缩触发 Token 阈值、消息条数阈值、保留 Token 与压缩前落盘', 'agent', 1)")) {
            ps.setString(1, configValue);
            ps.setString(2, metadataJson);
            ps.executeUpdate();
            log.info("播种默认 Bot 配置：agent.compaction");
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
