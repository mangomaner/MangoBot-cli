package io.github.mangomaner.mangobot.handler;

import io.github.mangomaner.mangobot.annotation.MangoBot;
import io.github.mangomaner.mangobot.annotation.MangoBotEventListener;
import io.github.mangomaner.mangobot.annotation.PluginPriority;
import io.github.mangomaner.mangobot.model.onebot.event.message.GroupMessageEvent;

@MangoBot
public class MessageHandler {
    @MangoBotEventListener
    @PluginPriority(100)
    public boolean onMessage(GroupMessageEvent event) {
        System.out.println("收到消息: " + event.getMessage());
        return true;
    }
}
