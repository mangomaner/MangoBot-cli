package io.github.mangomaner.mangobot.plugin;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class PluginRuntimeWrapper {
    private final String id;
    private final PluginClassLoader classLoader;
    
    @Setter
    private Plugin pluginInstance;
    
    private final List<String> controllerBeanNames = new ArrayList<>();
    private final List<Object> listenerInstances = new ArrayList<>();
    private final Map<String, List<RequestMappingInfo>> controllerMappings = new HashMap<>();

    public PluginRuntimeWrapper(String id, PluginClassLoader classLoader) {
        this.id = id;
        this.classLoader = classLoader;
    }
}
