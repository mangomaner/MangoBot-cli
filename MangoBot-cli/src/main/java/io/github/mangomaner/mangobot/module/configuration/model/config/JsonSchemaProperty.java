package io.github.mangomaner.mangobot.module.configuration.model.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * JSON schema 中的单个属性定义（叶子字段或嵌套 object）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JsonSchemaProperty implements Serializable {

    /** 显示名 */
    private String title;

    /** 字段类型：object | string | integer | number | boolean */
    private String type;

    /** 仅 type=object 时：子属性定义 */
    private Map<String, JsonSchemaProperty> properties;

    /** 缺省值（缺省字段由前端用此值补齐） */
    private Object defaultValue;

    /** 数值字段下限 */
    private Integer min;

    /** 数值字段上限 */
    private Integer max;

    /** 说明文案 */
    private String description;

    public static JsonSchemaProperty object(String title, Map<String, JsonSchemaProperty> properties) {
        return JsonSchemaProperty.builder()
                .title(title)
                .type("object")
                .properties(properties)
                .build();
    }

    public static JsonSchemaProperty integer(String title, int defaultValue, int min, String description) {
        return JsonSchemaProperty.builder()
                .title(title)
                .type("integer")
                .defaultValue(defaultValue)
                .min(min)
                .description(description)
                .build();
    }

    public static JsonSchemaProperty bool(String title, boolean defaultValue, String description) {
        return JsonSchemaProperty.builder()
                .title(title)
                .type("boolean")
                .defaultValue(defaultValue)
                .description(description)
                .build();
    }
}
