package org.mango;

import org.mango.mangobot.annotation.MangoBotEventListener;
import org.mango.mangobot.annotation.MangoBotHandler;
import org.mango.mangobot.model.onebot.event.message.PrivateMessageEvent;

@MangoBotHandler
public class TestListener {
    @MangoBotEventListener
    public boolean onPrivateMessage(PrivateMessageEvent event){
        System.out.println("插件成功收到私聊消息: " + event.getRawMessage());
        return false;
    }
}
