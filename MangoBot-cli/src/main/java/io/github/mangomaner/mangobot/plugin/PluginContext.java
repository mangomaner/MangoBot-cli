package io.github.mangomaner.mangobot.plugin;

import org.springframework.context.ApplicationContext;

public class PluginContext {
    private final ApplicationContext springContext;

    public PluginContext(ApplicationContext springContext) {
        this.springContext = springContext;
    }

    public <T> T getBean(Class<T> beanClass) {
        return springContext.getBean(beanClass);
    }

    public Object getBean(String name) {
        return springContext.getBean(name);
    }
}