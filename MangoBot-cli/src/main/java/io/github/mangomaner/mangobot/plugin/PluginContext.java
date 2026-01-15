package io.github.mangomaner.mangobot.plugin;

import io.github.mangomaner.mangobot.service.OneBotApiService;
import lombok.Getter;
import org.springframework.context.ApplicationContext;

public class PluginContext {
    private final ApplicationContext springContext;
    @Getter
    private final OneBotApiService oneBotApiService;

    public PluginContext(ApplicationContext springContext, OneBotApiService oneBotApiService) {
        this.springContext = springContext;
        this.oneBotApiService = oneBotApiService;
    }

    public <T> T getBean(Class<T> beanClass) {
        return springContext.getBean(beanClass);
    }

    public Object getBean(String name) {
        return springContext.getBean(name);
    }

}