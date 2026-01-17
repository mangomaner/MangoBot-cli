package io.github.mangomaner.mangobot.handler;

import io.github.mangomaner.mangobot.annotation.MangoBot;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.annotation.PluginPriority;
import io.github.mangomaner.mangobot.model.onebot.event.MessageEvent;
import io.github.mangomaner.mangobot.model.onebot.event.message.GroupMessageEvent;
import io.github.mangomaner.mangobot.model.onebot.event.message.PrivateMessageEvent;
import org.springframework.stereotype.Component;

@Component
public class MessageHandler {

    @MangoBotEventListener
    @PluginPriority(-1)
    public boolean onGroupMessage(GroupMessageEvent event) {
        System.out.println("收到消息: " + event.getMessage());
        return true;
    }

    @MangoBotEventListener
    @PluginPriority(-1)
    public boolean onPrivateMessage(PrivateMessageEvent event) {
        System.out.println("收到消息: " + event.getMessage());
        return true;
    }
}
