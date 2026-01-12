package org.mango.mangobot.manager.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BotStartupRunner implements CommandLineRunner {

    private final BotMessageHandler botMessageHandler;

    @Value("${server.port}")
    private String port;

    public BotStartupRunner(BotMessageHandler botMessageHandler) {
        this.botMessageHandler = botMessageHandler;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("WebSocket 服务器已启动，监听 ws://localhost:" + port);
    }
}