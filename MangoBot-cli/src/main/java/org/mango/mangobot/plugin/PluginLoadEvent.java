package org.mango.mangobot.plugin;

import org.springframework.context.ApplicationEvent;

// PluginLoadEvent.java
public class PluginLoadEvent extends ApplicationEvent {
    public PluginLoadEvent(Object source) {
        super(source);
    }
}