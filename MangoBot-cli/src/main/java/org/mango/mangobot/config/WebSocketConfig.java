package org.mango.mangobot.config;

import org.mango.mangobot.model.QQ.QQMessage;
import org.mango.mangobot.plugin.RegisteredHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketSession;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
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

    /**
     * 保存发送消息的echo值，等待回调获取消息id
     */
    @Bean
    public Map<String, QQMessage> echoMap() { return new HashMap<>(); }

    /**
     * 保存注解类型与其对应方法的映射关系，便于后续消息分发
     */
    @Bean
    public Map<Method, RegisteredHandler> messageHandlers(){return new HashMap<>();}
}