package org.mango;

import io.github.mangomaner.mangobot.annotation.MangoBot;
import io.github.mangomaner.mangobot.annotation.MangoBotEventListener;
import io.github.mangomaner.mangobot.annotation.PluginPriority;
import io.github.mangomaner.mangobot.model.onebot.event.message.GroupMessageEvent;
import io.github.mangomaner.mangobot.plugin.Plugin;
import io.github.mangomaner.mangobot.plugin.PluginContext;
import io.github.mangomaner.mangobot.service.OneBotApiService;

import java.util.logging.Logger;

@MangoBot
public class ExamplePlugin implements Plugin {

    private static final Logger logger = Logger.getLogger(ExamplePlugin.class.getName());

    public ExamplePlugin() {

    }

    @Override
    public void onEnable(PluginContext context) {
        OneBotApiService oneBotApiService = (OneBotApiService) context.getBean("oneBotApiService");
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