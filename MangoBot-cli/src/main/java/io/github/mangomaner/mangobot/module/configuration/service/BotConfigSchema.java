package io.github.mangomaner.mangobot.module.configuration.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 新增的 bot 配置项在**老库**里的兜底补种。
 *
 * <p>本项目没有迁移机制：{@code schema.sql} 只在 DB 文件不存在时执行，老库长不出新配置行，
 * 于是配置页上看不到、代码读到的永远是默认值。和 {@code SimSchema} / {@code FileSchema} 是同一套办法。
 *
 * <p><b>不能用 {@code INSERT OR IGNORE} 靠唯一约束去重</b>：{@code bot_configs} 上那条
 * {@code UNIQUE(bot_id, config_key)} 在 {@code bot_id} 为 NULL 时**管不住**——SQL 里 NULL 不等于 NULL，
 * 唯一约束不生效，重复执行会插出多行同样的默认配置。所以这里先查再插。
 */
@Component
@Slf4j
public class BotConfigSchema {

    /** 一条要补种的默认配置。与 {@code schema.sql} 里那行必须保持一致。 */
    private record Seed(String key, String value, String type, String metadata,
                        String description, String explain, String category) {
    }

    private static final Seed COMPACT_THRESHOLD = new Seed(
            "claude.compact_threshold_k", "30", "INTEGER", "{\"min\":0,\"max\":500}",
            "上下文压缩阈值（k）",
            "一轮结束时上下文超过这个值就压缩一次；0 表示关闭。单位是千 token",
            "context");

    private final DataSource dataSource;

    public BotConfigSchema(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void init() {
        try (Connection connection = dataSource.getConnection()) {
            seedIfMissing(connection, COMPACT_THRESHOLD);
        } catch (Exception e) {
            log.error("补种 bot 配置失败，配置页上可能看不到新项", e);
        }
    }

    /** 默认行（{@code bot_id IS NULL}）不存在才插。 */
    private static void seedIfMissing(Connection connection, Seed seed) throws Exception {
        try (PreparedStatement exists = connection.prepareStatement(
                "SELECT COUNT(*) FROM bot_configs WHERE bot_id IS NULL AND config_key = ?")) {
            exists.setString(1, seed.key());
            try (ResultSet rs = exists.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO bot_configs
                    (bot_id, config_key, config_value, config_type, metadata, description, explain, category, editable)
                VALUES (NULL, ?, ?, ?, ?, ?, ?, ?, 1)""")) {
            insert.setString(1, seed.key());
            insert.setString(2, seed.value());
            insert.setString(3, seed.type());
            insert.setString(4, seed.metadata());
            insert.setString(5, seed.description());
            insert.setString(6, seed.explain());
            insert.setString(7, seed.category());
            insert.executeUpdate();
            log.info("已补种 bot 配置：{} = {}", seed.key(), seed.value());
        }
    }
}
