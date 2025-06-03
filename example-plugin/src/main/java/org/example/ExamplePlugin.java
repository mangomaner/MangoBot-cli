package org.example;

import org.mango.mangobot.annotation.PluginPriority;
import org.mango.mangobot.annotation.QQ.method.TextMessage;
import org.mango.mangobot.annotation.QQ.parameter.Content;
import org.mango.mangobot.annotation.QQ.parameter.SenderId;
import org.mango.mangobot.plugin.Plugin;
import org.mango.mangobot.plugin.PluginContext;

public class ExamplePlugin implements Plugin {
    @Override
    public void onEnable(PluginContext context) {
        System.out.println("ExamplePlugin 已启用");
    }

    @Override
    public void onDisable() {
        System.out.println("ExamplePlugin 已禁用");
    }

    @TextMessage
    @PluginPriority(100)
    public void handleTextMessage(@SenderId String fromUser, @Content String content) {
        System.out.println("[ExamplePlugin] 收到文本消息：" + content + " from: " + fromUser);
    }
}
