package org.mango.mangobot.handler;

import org.mango.mangobot.model.onebot.event.message.GroupMessageEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MessageHandler {
    @EventListener
    public void onMessage(GroupMessageEvent event) {
        System.out.println("收到消息: " + event.getMessage());
    }
}
