package org.mango.mangobot;

import org.mango.mangobot.plugin.PluginManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {RedisAutoConfiguration.class})
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class MangoBotApplication {
//    public static void main(String[] args) {
//        SpringApplication.run(MangoBotApplication.class, args);
//    }

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(MangoBotApplication.class, args);

        PluginManager manager = context.getBean(PluginManager.class);
        manager.loadPlugins();
    }
}
