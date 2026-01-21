package io.github.mangomaner.mangobot.model.dto.config;

import lombok.Data;

@Data
public class CreateConfigRequest {
    private Long id;
    private String key;
    private String value;
    private String desc;
    private String explain;
    private String type;
}
