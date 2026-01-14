package org.mango;

import io.github.mangomaner.mangobot.annotation.MangoBot;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotApiService;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.annotation.PluginPriority;
import io.github.mangomaner.mangobot.annotation.web.MangoBotController;
import io.github.mangomaner.mangobot.annotation.web.MangoBotRequestMapping;
import io.github.mangomaner.mangobot.annotation.web.MangoRequestMethod;
import io.github.mangomaner.mangobot.model.onebot.event.message.GroupMessageEvent;
import io.github.mangomaner.mangobot.plugin.Plugin;
import io.github.mangomaner.mangobot.plugin.PluginContext;
import io.github.mangomaner.mangobot.service.OneBotApiService;

import java.util.logging.Logger;

// 这里包含了所有能注入的东西，包括web部分
@MangoBot
@MangoBotController
@MangoBotRequestMapping("/plugin")
public class ExamplePlugin implements Plugin {

    @MangoBotApiService
    private OneBotApiService oneBotApiService;

    private static final Logger logger = Logger.getLogger(ExamplePlugin.class.getName());

    public ExamplePlugin() {

    }

    @Override
    public void onEnable(PluginContext context) {
        // OneBotApiService oneBotApiService = (OneBotApiService) context.getBean("oneBotApiService");
        // 另一线程延时10秒后执行
        System.out.println("准备开始");
        new Thread(() -> {
            try {
                Thread.sleep(10000);
                System.out.println(oneBotApiService.canSendImage(1461626638));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
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

    @MangoBotRequestMapping(value = "/ok", method = MangoRequestMethod.GET)
    public String hello() {
        return "Hello World!";
    }


}