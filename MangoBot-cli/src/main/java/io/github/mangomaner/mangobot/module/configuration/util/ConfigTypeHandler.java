package io.github.mangomaner.mangobot.module.configuration.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.module.configuration.enums.ConfigType;
import io.github.mangomaner.mangobot.module.configuration.model.config.KeyValueItem;
import io.github.mangomaner.mangobot.module.configuration.model.config.RangeValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Component
public class ConfigTypeHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

    public String serialize(ConfigType type, Object value) {
        if (value == null) {
            return null;
        }

        try {
            switch (type) {
                case STRING:
                case URL:
                case PASSWORD:
                case COLOR:
                case LINK:
                    return value.toString();

                case INTEGER:
                case LONG:
                case DOUBLE:
                    return value.toString();

                case BOOLEAN:
                    if (value instanceof Boolean) {
                        return ((Boolean) value) ? "true" : "false";
                    }
                    return value.toString();

                case SELECT:
                    return value.toString();

                case MULTI_SELECT:
                    if (value instanceof List) {
                        return OBJECT_MAPPER.writeValueAsString(value);
                    }
                    return value.toString();

                case DATE:
                    if (value instanceof LocalDate) {
                        return ((LocalDate) value).format(DATE_FORMATTER);
                    }
                    if (value instanceof Long) {
                        return Instant.ofEpochMilli((Long) value)
                                .atZone(DEFAULT_ZONE)
                                .toLocalDate()
                                .format(DATE_FORMATTER);
                    }
                    return value.toString();

                case DATETIME:
                    if (value instanceof LocalDateTime) {
                        return ((LocalDateTime) value).format(DATETIME_FORMATTER);
                    }
                    if (value instanceof Long) {
                        return Instant.ofEpochMilli((Long) value)
                                .atZone(DEFAULT_ZONE)
                                .toLocalDateTime()
                                .format(DATETIME_FORMATTER);
                    }
                    return value.toString();

                case TIMESTAMP:
                    if (value instanceof Long) {
                        return value.toString();
                    }
                    if (value instanceof Date) {
                        return String.valueOf(((Date) value).getTime());
                    }
                    if (value instanceof LocalDateTime) {
                        return String.valueOf(((LocalDateTime) value)
                                .atZone(DEFAULT_ZONE)
                                .toInstant()
                                .toEpochMilli());
                    }
                    return value.toString();

                case LIST:
                    if (value instanceof List) {
                        return OBJECT_MAPPER.writeValueAsString(value);
                    }
                    return value.toString();

                case KEY_VALUE:
                    if (value instanceof Map) {
                        return OBJECT_MAPPER.writeValueAsString(value);
                    }
                    return value.toString();

                case KEY_VALUE_LIST:
                    if (value instanceof List) {
                        return OBJECT_MAPPER.writeValueAsString(value);
                    }
                    return value.toString();

                case RANGE_INTEGER:
                case RANGE_DOUBLE:
                    if (value instanceof RangeValue) {
                        return OBJECT_MAPPER.writeValueAsString(value);
                    }
                    return value.toString();

                case JSON:
                    if (value instanceof String) {
                        return (String) value;
                    }
                    return OBJECT_MAPPER.writeValueAsString(value);

                case GROUP_LIST_SELECTOR:
                case PRIVATE_LIST_SELECTOR:
                    if (value instanceof List) {
                        return OBJECT_MAPPER.writeValueAsString(value);
                    }
                    return value.toString();

                default:
                    return value.toString();
            }
        } catch (JsonProcessingException e) {
            log.error("序列化配置值失败: type={}, value={}", type, value, e);
            return value.toString();
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T deserialize(ConfigType type, String value, Class<T> targetClass) {
        if (value == null || value.isEmpty()) {
            return getDefaultValue(type, targetClass);
        }

        try {
            switch (type) {
                case STRING:
                case URL:
                case PASSWORD:
                case COLOR:
                case LINK:
                case SELECT:
                    return (T) value;

                case INTEGER:
                    if (targetClass == Integer.class) {
                        return (T) Integer.valueOf(value);
                    }
                    return (T) value;

                case LONG:
                    if (targetClass == Long.class) {
                        return (T) Long.valueOf(value);
                    }
                    return (T) value;

                case DOUBLE:
                    if (targetClass == Double.class) {
                        return (T) Double.valueOf(value);
                    }
                    return (T) value;

                case BOOLEAN:
                    if (targetClass == Boolean.class) {
                        return (T) Boolean.valueOf(parseBoolean(value));
                    }
                    return (T) value;

                case MULTI_SELECT:
                    if (targetClass == List.class) {
                        return (T) OBJECT_MAPPER.readValue(value, new TypeReference<List<String>>() {});
                    }
                    return (T) value;

                case DATE:
                    if (targetClass == LocalDate.class) {
                        return (T) LocalDate.parse(value, DATE_FORMATTER);
                    }
                    if (targetClass == Long.class) {
                        LocalDate date = LocalDate.parse(value, DATE_FORMATTER);
                        return (T) Long.valueOf(date.atStartOfDay(DEFAULT_ZONE).toInstant().toEpochMilli());
                    }
                    return (T) value;

                case DATETIME:
                    if (targetClass == LocalDateTime.class) {
                        return (T) LocalDateTime.parse(value, DATETIME_FORMATTER);
                    }
                    if (targetClass == Long.class) {
                        LocalDateTime dateTime = LocalDateTime.parse(value, DATETIME_FORMATTER);
                        return (T) Long.valueOf(dateTime.atZone(DEFAULT_ZONE).toInstant().toEpochMilli());
                    }
                    return (T) value;

                case TIMESTAMP:
                    if (targetClass == Long.class) {
                        return (T) Long.valueOf(value);
                    }
                    if (targetClass == LocalDateTime.class) {
                        Long timestamp = Long.valueOf(value);
                        return (T) LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), DEFAULT_ZONE);
                    }
                    return (T) value;

                case LIST:
                    if (targetClass == List.class) {
                        return (T) OBJECT_MAPPER.readValue(value, new TypeReference<List<String>>() {});
                    }
                    return (T) value;

                case KEY_VALUE:
                    if (targetClass == Map.class) {
                        return (T) OBJECT_MAPPER.readValue(value, new TypeReference<Map<String, String>>() {});
                    }
                    return (T) value;

                case KEY_VALUE_LIST:
                    if (targetClass == List.class) {
                        return (T) OBJECT_MAPPER.readValue(value, new TypeReference<List<KeyValueItem>>() {});
                    }
                    return (T) value;

                case RANGE_INTEGER:
                    if (targetClass == RangeValue.class) {
                        Map<String, Object> map = OBJECT_MAPPER.readValue(value, new TypeReference<Map<String, Object>>() {});
                        Integer min = map.get("min") != null ? ((Number) map.get("min")).intValue() : null;
                        Integer max = map.get("max") != null ? ((Number) map.get("max")).intValue() : null;
                        Integer step = map.get("step") != null ? ((Number) map.get("step")).intValue() : null;
                        return (T) new RangeValue<>(min, max, step);
                    }
                    return (T) value;

                case RANGE_DOUBLE:
                    if (targetClass == RangeValue.class) {
                        Map<String, Object> map = OBJECT_MAPPER.readValue(value, new TypeReference<Map<String, Object>>() {});
                        Double min = map.get("min") != null ? ((Number) map.get("min")).doubleValue() : null;
                        Double max = map.get("max") != null ? ((Number) map.get("max")).doubleValue() : null;
                        Double step = map.get("step") != null ? ((Number) map.get("step")).doubleValue() : null;
                        return (T) new RangeValue<>(min, max, step);
                    }
                    return (T) value;

                case JSON:
                    if (targetClass == String.class) {
                        return (T) value;
                    }
                    return OBJECT_MAPPER.readValue(value, targetClass);

                case GROUP_LIST_SELECTOR:
                case PRIVATE_LIST_SELECTOR:
                    if (targetClass == List.class) {
                        return (T) OBJECT_MAPPER.readValue(value, new TypeReference<List<Long>>() {});
                    }
                    return (T) value;

                default:
                    return (T) value;
            }
        } catch (JsonProcessingException | DateTimeParseException | NumberFormatException e) {
            log.error("反序列化配置值失败: type={}, value={}", type, value, e);
            return getDefaultValue(type, targetClass);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getDefaultValue(ConfigType type, Class<T> targetClass) {
        try {
            switch (type) {
                case STRING:
                case URL:
                case PASSWORD:
                case COLOR:
                case SELECT:
                    return (T) "";

                case INTEGER:
                    return (T) Integer.valueOf(0);

                case LONG:
                case TIMESTAMP:
                    return (T) Long.valueOf(0L);

                case DOUBLE:
                    return (T) Double.valueOf(0.0);

                case BOOLEAN:
                    return (T) Boolean.FALSE;

                case MULTI_SELECT:
                case LIST:
                case GROUP_LIST_SELECTOR:
                case PRIVATE_LIST_SELECTOR:
                    return (T) Collections.emptyList();

                case KEY_VALUE:
                    return (T) Collections.emptyMap();

                case KEY_VALUE_LIST:
                    return (T) Collections.emptyList();

                case RANGE_INTEGER:
                    return (T) new RangeValue<>(0, 100, 1);

                case RANGE_DOUBLE:
                    return (T) new RangeValue<>(0.0, 1.0, 0.1);

                case JSON:
                    if (targetClass == String.class) {
                        return (T) "{}";
                    }
                    return null;

                default:
                    return null;
            }
        } catch (ClassCastException e) {
            return null;
        }
    }

    public boolean validate(ConfigType type, String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }

        try {
            switch (type) {
                case INTEGER:
                    Integer.parseInt(value);
                    return true;

                case LONG:
                case TIMESTAMP:
                    Long.parseLong(value);
                    return true;

                case DOUBLE:
                    Double.parseDouble(value);
                    return true;

                case BOOLEAN:
                    parseBoolean(value);
                    return true;

                case MULTI_SELECT:
                    OBJECT_MAPPER.readValue(value, new TypeReference<List<String>>() {});
                    return true;

                case DATE:
                    LocalDate.parse(value, DATE_FORMATTER);
                    return true;

                case DATETIME:
                    LocalDateTime.parse(value, DATETIME_FORMATTER);
                    return true;

                case LIST:
                    OBJECT_MAPPER.readValue(value, new TypeReference<List<String>>() {});
                    return true;

                case GROUP_LIST_SELECTOR:
                case PRIVATE_LIST_SELECTOR:
                    OBJECT_MAPPER.readValue(value, new TypeReference<List<Long>>() {});
                    return true;

                case KEY_VALUE:
                    OBJECT_MAPPER.readValue(value, new TypeReference<Map<String, String>>() {});
                    return true;

                case KEY_VALUE_LIST:
                    OBJECT_MAPPER.readValue(value, new TypeReference<List<KeyValueItem>>() {});
                    return true;

                case RANGE_INTEGER:
                case RANGE_DOUBLE:
                    OBJECT_MAPPER.readValue(value, new TypeReference<Map<String, Object>>() {});
                    return true;

                case JSON:
                    OBJECT_MAPPER.readTree(value);
                    return true;

                case URL:
                    return value.startsWith("http://") || value.startsWith("https://") || value.startsWith("ws://") || value.startsWith("wss://");

                case COLOR:
                    return value.matches("^#[0-9A-Fa-f]{6}$") || value.matches("^#[0-9A-Fa-f]{3}$");

                default:
                    return true;
            }
        } catch (Exception e) {
            log.warn("配置值验证失败: type={}, value={}", type, value);
            return false;
        }
    }

    public String getDisplayValue(ConfigType type, String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        switch (type) {
            case BOOLEAN:
                return parseBoolean(value) ? "是" : "否";

            case TIMESTAMP:
                try {
                    long timestamp = Long.parseLong(value);
                    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            .format(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), DEFAULT_ZONE));
                } catch (NumberFormatException e) {
                    return value;
                }

            case PASSWORD:
                return "******";

            case JSON:
            case KEY_VALUE:
            case KEY_VALUE_LIST:
            case MULTI_SELECT:
            case LIST:
            case RANGE_INTEGER:
            case RANGE_DOUBLE:
            case GROUP_LIST_SELECTOR:
            case PRIVATE_LIST_SELECTOR:
                if (value.length() > 50) {
                    return value.substring(0, 50) + "...";
                }
                return value;

            default:
                return value;
        }
    }

    private boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }
}
