package io.github.mangomaner.mangobot.module.configuration.model.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 统一的 JSON 配置 schema（轻量 JSON Schema 子集）。
 *
 * <p>存放于 {@link ConfigMetadata#getSchema()}，驱动前端 SchemaForm 把任意 JSON 类型配置渲染成
 * 结构化表单；后端仅在播种时序列化该结构，不参与运行时解析。后续新增 JSON 配置只需提供一段 schema。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JsonConfigSchema implements Serializable {

    /** 根类型，当前约定为 "object" */
    private String type;

    /** 键 -> 属性定义，顺序即前端展示顺序 */
    private Map<String, JsonSchemaProperty> properties;
}
