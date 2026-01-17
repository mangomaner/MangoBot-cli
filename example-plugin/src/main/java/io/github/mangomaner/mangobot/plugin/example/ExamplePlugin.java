package io.github.mangomaner.mangobot.plugin.example;

import io.github.mangomaner.mangobot.annotation.MangoBotApiService;
import io.github.mangomaner.mangobot.annotation.PluginDescribe;
import io.github.mangomaner.mangobot.annotation.PluginPriority;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.annotation.web.MangoBotRequestMapping;
import io.github.mangomaner.mangobot.annotation.web.MangoRequestMethod;
import io.github.mangomaner.mangobot.model.onebot.event.message.GroupMessageEvent;
import io.github.mangomaner.mangobot.plugin.Plugin;
import io.github.mangomaner.mangobot.plugin.PluginContext;
import io.github.mangomaner.mangobot.service.OneBotApiService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.logging.Logger;

// 这里包含了所有能注入的东西，包括web部分
@MangoBotRequestMapping("/plugin")
@PluginDescribe(name = "ExamplePlugin", author = "mangomaner", version = "1.0.0", description = "一个示例插件")
public class ExamplePlugin implements Plugin {

    private static final Logger logger = Logger.getLogger("123");
    private AnnotationConfigApplicationContext applicationContext;

    @MangoBotApiService
    private OneBotApiService oneBotApiService;

    public ExamplePlugin() {

    }

    @Override
    public void onEnable() {
        applicationContext = new AnnotationConfigApplicationContext();
        // 扫描org.mango包
        applicationContext.scan("io.github.mangomaner.mangobot.plugin.example");
        applicationContext.refresh();

        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            System.out.println("Loaded Bean: " + beanName);
        }

        logger.info("ExamplePlugin 已启用，Spring容器初始化完成");
    }

    @Override
    public void onDisable() {
        if (applicationContext != null) {
            applicationContext.close();
        }
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