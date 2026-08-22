package io.github.mangomaner.mangobot.module.configuration.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ConfigType {
    
    STRING("STRING", "字符串", false),
    INTEGER("INTEGER", "整数", false),
    LONG("LONG", "长整数", false),
    DOUBLE("DOUBLE", "浮点数", false),
    BOOLEAN("BOOLEAN", "布尔值", false),
    SELECT("SELECT", "单选下拉", true),
    MULTI_SELECT("MULTI_SELECT", "多选下拉", true),
    DATE("DATE", "日期", false),
    DATETIME("DATETIME", "日期时间", false),
    TIMESTAMP("TIMESTAMP", "时间戳", false),
    LIST("LIST", "字符串列表", false),
    KEY_VALUE("KEY_VALUE", "键值对", false),
    KEY_VALUE_LIST("KEY_VALUE_LIST", "键值对列表", false),
    RANGE_INTEGER("RANGE_INTEGER", "整数范围", true),
    RANGE_DOUBLE("RANGE_DOUBLE", "浮点范围", true),
    JSON("JSON", "JSON对象", false),
    COLOR("COLOR", "颜色", false),
    URL("URL", "网址链接", false),
    PASSWORD("PASSWORD", "密码", false),
    LINK("LINK", "链接", false),
    GROUP_LIST_SELECTOR("GROUP_LIST_SELECTOR", "群列表选择器", true),
    PRIVATE_LIST_SELECTOR("PRIVATE_LIST_SELECTOR", "私聊列表选择器", true);

    @JsonValue
    private final String code;
    private final String description;
    private final boolean requiresMetadata;

    ConfigType(String code, String description, boolean requiresMetadata) {
        this.code = code;
        this.description = description;
        this.requiresMetadata = requiresMetadata;
    }

    public static ConfigType fromCode(String code) {
        if (code == null) {
            return STRING;
        }
        for (ConfigType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return STRING;
    }

    public static boolean isValid(String code) {
        if (code == null) {
            return false;
        }
        for (ConfigType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }

    public boolean isNumericType() {
        return this == INTEGER || this == LONG || this == DOUBLE;
    }

    public boolean isDateType() {
        return this == DATE || this == DATETIME || this == TIMESTAMP;
    }

    public boolean isJsonBasedType() {
        return this == JSON || this == LIST || this == KEY_VALUE || 
               this == KEY_VALUE_LIST || this == MULTI_SELECT ||
               this == RANGE_INTEGER || this == RANGE_DOUBLE ||
               this == GROUP_LIST_SELECTOR || this == PRIVATE_LIST_SELECTOR;
    }
}
