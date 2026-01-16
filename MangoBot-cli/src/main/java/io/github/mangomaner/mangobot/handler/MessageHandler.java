package io.github.mangomaner.mangobot.handler;

import io.github.mangomaner.mangobot.annotation.MangoBot;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.annotation.PluginPriority;
import io.github.mangomaner.mangobot.model.onebot.event.message.GroupMessageEvent;
import org.springframework.stereotype.Component;

@Component
public class MessageHandler {

    @MangoBotEventListener
    @PluginPriority(-1)
    public boolean onMessage(GroupMessageEvent event) {
        System.out.println("收到消息: " + event.getMessage());
        return true;
    }
}
