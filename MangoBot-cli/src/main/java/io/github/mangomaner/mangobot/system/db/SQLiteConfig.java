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
        };
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
