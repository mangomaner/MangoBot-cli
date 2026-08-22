package io.github.mangomaner.mangobot.module.configuration.model.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigMetadata implements Serializable {
    
    private List<SelectOption> options;
    
    private Integer min;
    
    private Integer max;
    
    private Double minDouble;
    
    private Double maxDouble;
    
    private Double step;
    
    private String placeholder;
    
    private String keyPlaceholder;
    
    private String valuePlaceholder;
    
    private String format;
    
    private String itemType;
    
    private Boolean clearable;
    
    private Boolean filterable;
    
    private Boolean showPassword;
    
    private Integer rows;
    
    private Integer maxLength;
    
    private String pattern;
    
    private String listType;

    private JsonConfigSchema schema;

    private Map<String, Object> extra;

    public static ConfigMetadata forSelect(List<SelectOption> options) {
        ConfigMetadata metadata = new ConfigMetadata();
        metadata.setOptions(options);
        return metadata;
    }
    
    public static ConfigMetadata forRange(Integer min, Integer max, Integer step) {
        ConfigMetadata metadata = new ConfigMetadata();
        metadata.setMin(min);
        metadata.setMax(max);
        metadata.setStep(step != null ? step.doubleValue() : null);
        return metadata;
    }
    
    public static ConfigMetadata forRange(Double min, Double max, Double step) {
        ConfigMetadata metadata = new ConfigMetadata();
        metadata.setMinDouble(min);
        metadata.setMaxDouble(max);
        metadata.setStep(step);
        return metadata;
    }
    
    public static ConfigMetadata forPlaceholder(String placeholder) {
        ConfigMetadata metadata = new ConfigMetadata();
        metadata.setPlaceholder(placeholder);
        return metadata;
    }
    
    public static ConfigMetadata forGroupListSelector() {
        ConfigMetadata metadata = new ConfigMetadata();
        metadata.setListType("group");
        return metadata;
    }
    
    public static ConfigMetadata forPrivateListSelector() {
        ConfigMetadata metadata = new ConfigMetadata();
        metadata.setListType("private");
        return metadata;
    }
}
