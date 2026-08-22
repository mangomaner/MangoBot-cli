package io.github.mangomaner.mangobot.module.agent.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.configuration.model.config.JsonConfigSchema;
import io.github.mangomaner.mangobot.module.configuration.model.config.JsonSchemaProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按 Bot + 来源（web/group/private）区分的上下文压缩阈值配置值。
 *
 * <p>对应 bot_configs 中 config_key = {@link #CONFIG_KEY}、config_type = JSON 的配置值；
 * 字段用包装类型，缺失字段为 null，由 {@link AgentRuntimeRegistry} 回退到
 * {@link #defaults()} 的默认值，避免部分配置误覆盖默认值。
 */
public record AgentCompactionConfig(
        SourceCompaction web,
        SourceCompaction group,
        @JsonProperty("private") SourceCompaction privateSource) {

    /** bot_configs 中的配置 key（默认播种与 Bot 专属共用） */
    public static final String CONFIG_KEY = "agent.compaction";

    public record SourceCompaction(
            Integer triggerTokens,
            Integer triggerMessages,
            Integer keepTokens,
            Boolean flushBeforeCompact) {
    }

    /** 三端默认值：60K 触发 / 消息条数触发关闭 / 保留 30K / 压缩前落盘。默认值的唯一事实来源 */
    public static AgentCompactionConfig defaults() {
        SourceCompaction d = new SourceCompaction(60000, 0, 30000, true);
        return new AgentCompactionConfig(d, d, d);
    }

    /** 取指定来源的压缩配置，来源子对象缺失时回退默认值 */
    public SourceCompaction sourceFor(SessionSource source) {
        return switch (source) {
            case WEB -> web != null ? web : defaults().web();
            case GROUP -> group != null ? group : defaults().group();
            case PRIVATE -> privateSource != null ? privateSource : defaults().privateSource();
        };
    }

    /** 前端结构化表单的 schema；字段缺省值取自 {@link #defaults()}（与配置值同一事实来源） */
    public static JsonConfigSchema schema() {
        SourceCompaction d = defaults().web();
        return JsonConfigSchema.builder()
                .type("object")
                .properties(ordered(
                        Map.entry("web", sourceSchema("Web 端", d)),
                        Map.entry("group", sourceSchema("群聊", d)),
                        Map.entry("private", sourceSchema("私聊", d))))
                .build();
    }

    private static JsonSchemaProperty sourceSchema(String title, SourceCompaction d) {
        return JsonSchemaProperty.object(title, ordered(
                Map.entry("triggerTokens", JsonSchemaProperty.integer(
                        "触发 Token 阈值", d.triggerTokens(), 0, "估算上下文超过该 token 数触发压缩，0 表示关闭")),
                Map.entry("triggerMessages", JsonSchemaProperty.integer(
                        "触发消息条数", d.triggerMessages(), 0, "消息条数超过该值触发压缩，0 表示关闭")),
                Map.entry("keepTokens", JsonSchemaProperty.integer(
                        "保留 Token", d.keepTokens(), 0, "压缩后保留尾部 token 预算")),
                Map.entry("flushBeforeCompact", JsonSchemaProperty.bool(
                        "压缩前落盘", d.flushBeforeCompact(), "压缩前先将新事实写入记忆日流水账"))));
    }

    /** LinkedHashMap 保证 schema 字段展示顺序；@SafeVarargs + Map.entry 保证类型安全 */
    @SafeVarargs
    private static Map<String, JsonSchemaProperty> ordered(Map.Entry<String, JsonSchemaProperty>... entries) {
        Map<String, JsonSchemaProperty> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonSchemaProperty> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }
}
