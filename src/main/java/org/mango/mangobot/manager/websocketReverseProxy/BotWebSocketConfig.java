package org.mango.mangobot.manager.websocketReverseProxy;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class BotWebSocketConfig implements WebSocketConfigurer {

    private final BotMessageHandler botMessageHandler;

    public BotWebSocketConfig(BotMessageHandler botMessageHandler) {
        this.botMessageHandler = botMessageHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(botMessageHandler, "/")
                .setAllowedOrigins("*");
    }
}