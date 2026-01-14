package org.mango;

import io.github.mangomaner.mangobot.annotation.MangoBot;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.model.onebot.event.message.PrivateMessageEvent;

@MangoBot
public class TestListener {
    @MangoBotEventListener
    public boolean onPrivateMessage(PrivateMessageEvent event){
        System.out.println("插件成功收到私聊消息: " + event.getRawMessage());
        return false;
    }
}
