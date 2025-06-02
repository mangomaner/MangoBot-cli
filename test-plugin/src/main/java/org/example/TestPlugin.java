package org.example;

import org.mango.mangobot.annotation.QQ.method.TextMessage;
import org.mango.mangobot.annotation.QQ.parameter.Content;
import org.mango.mangobot.annotation.QQ.parameter.SenderId;
import org.mango.mangobot.plugin.Plugin;
import org.mango.mangobot.plugin.PluginContext;

public class TestPlugin implements Plugin {
    @Override
    public void onEnable(PluginContext context) {
        System.out.println("TestPlugin 已启用");
    }

    @Override
    public void onDisable() {
        System.out.println("TestPlugin 已禁用");
    }

    @TextMessage
    public void handleTextMessage(@SenderId String fromUser, @Content String content) {
        System.out.println("[TestPlugin] 收到文本消息：" + content + " from: " + fromUser);
    }
}
