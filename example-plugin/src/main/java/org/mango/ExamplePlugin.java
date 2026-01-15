package org.mango;

import io.github.mangomaner.mangobot.annotation.PluginDescribe;
import io.github.mangomaner.mangobot.annotation.PluginPriority;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.annotation.web.MangoBotRequestMapping;
import io.github.mangomaner.mangobot.annotation.web.MangoRequestMethod;
import io.github.mangomaner.mangobot.model.onebot.event.message.GroupMessageEvent;
import io.github.mangomaner.mangobot.plugin.Plugin;
import io.github.mangomaner.mangobot.plugin.PluginContext;

import java.util.logging.Logger;

// 这里包含了所有能注入的东西，包括web部分
@MangoBotRequestMapping("/plugin")
@PluginDescribe(name = "ExamplePlugin", author = "mangomaner", version = "1.0.0", description = "一个示例插件")
public class ExamplePlugin implements Plugin {

    private static final Logger logger = Logger.getLogger("123");

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
                System.out.println(context.getOneBotApiService().canSendImage(1461626638));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
        ZzzDependence zzzDependence = new ZzzDependence();
        zzzDependence.zzz();
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