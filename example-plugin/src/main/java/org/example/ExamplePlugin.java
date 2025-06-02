package org.example;

import org.mango.mangobot.plugin.Plugin;
import org.mango.mangobot.plugin.PluginContext;

public class ExamplePlugin implements Plugin {
    @Override
    public void onEnable(PluginContext context) {
        System.out.println("加载插件");
    }

    @Override
    public void onDisable() {

    }
}
