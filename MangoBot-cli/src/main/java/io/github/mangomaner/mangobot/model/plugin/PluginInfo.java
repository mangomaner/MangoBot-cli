package io.github.mangomaner.mangobot.model.plugin;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class PluginInfo implements Serializable {
    private String id;
    private boolean loaded;
    private String name;
    private String author;
    private String version;
    private String description;
}
