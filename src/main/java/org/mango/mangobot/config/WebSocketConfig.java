package org.mango.mangobot.config;

import org.mango.mangobot.model.QQ.QQMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;

@Configuration
public class WebSocketConfig {

    @Value("${QQ.whitelist}")
    private List<String> groupList;

    // 存储已连接对象
    @Bean
    public Map<String, WebSocketSession> sessionMap() {
        return new HashMap<>();
    }

    // 放置连接群号白名单
    @Bean
    public Set<String> groupSet() {
        return new HashSet<>(groupList);
    }

    @Bean
    public Map<String, QQMessage> echoMap() { return new HashMap<>(); }
}