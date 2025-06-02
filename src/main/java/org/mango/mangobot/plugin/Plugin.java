package org.mango.mangobot.plugin;

public interface Plugin {
    void onEnable(PluginContext context); // 插件启用时调用
    void onDisable();                      // 插件禁用时调用
}