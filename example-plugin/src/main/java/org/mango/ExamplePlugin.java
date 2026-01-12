package org.mango;

import org.mango.mangobot.annotation.MangoBotEventListener;
import org.mango.mangobot.annotation.MangoBotHandler;
import org.mango.mangobot.annotation.PluginPriority;
import org.mango.mangobot.model.onebot.event.message.GroupMessageEvent;
import org.mango.mangobot.plugin.Plugin;
import org.mango.mangobot.plugin.PluginContext;

import java.util.logging.Logger;

@MangoBotHandler
public class ExamplePlugin implements Plugin {

    private static final Logger logger = Logger.getLogger(ExamplePlugin.class.getName());

    public ExamplePlugin() {

    }

    @Override
    public void onEnable(PluginContext context) {
        logger.info("ExamplePlugin 已启用");
    }

    @Override
    public void onDisable() {
        logger.info("ExamplePlugin 已禁用");
    }

    @MangoBotEventListener
    @PluginPriority(5)
    public boolean onGroupMessage(GroupMessageEvent event){
        System.out.println("插件成功收到消息: " + event.getRawMessage());
        return false;
    }


}