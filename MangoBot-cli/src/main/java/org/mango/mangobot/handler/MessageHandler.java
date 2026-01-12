package org.mango.mangobot.handler;

import org.mango.mangobot.annotation.MangoBotEventListener;
import org.mango.mangobot.annotation.MangoBotHandler;
import org.mango.mangobot.annotation.PluginPriority;
import org.mango.mangobot.model.onebot.event.message.GroupMessageEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@MangoBotHandler
public class MessageHandler {
    @MangoBotEventListener
    @PluginPriority(100)
    public boolean onMessage(GroupMessageEvent event) {
        System.out.println("收到消息: " + event.getMessage());
        return true;
    }
}
