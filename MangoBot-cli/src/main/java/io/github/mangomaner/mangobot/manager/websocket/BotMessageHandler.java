package io.github.mangomaner.mangobot.manager.websocket;

import lombok.extern.slf4j.Slf4j;
import io.github.mangomaner.mangobot.manager.event.MangoEventPublisher;
import io.github.mangomaner.mangobot.model.onebot.event.Event;
import io.github.mangomaner.mangobot.model.onebot.event.EventParser;
import io.github.mangomaner.mangobot.model.onebot.event.meta.HeartbeatEvent;
import io.github.mangomaner.mangobot.model.onebot.event.meta.LifecycleEvent;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@Slf4j
public class BotMessageHandler extends TextWebSocketHandler implements HandshakeInterceptor {

    private final MangoEventPublisher eventPublisher;
    private final BotConnectionManager connectionManager;
    private final EchoHandler echoHandler;

    public BotMessageHandler(MangoEventPublisher eventPublisher, BotConnectionManager connectionManager, EchoHandler echoHandler) {
        this.eventPublisher = eventPublisher;
        this.connectionManager = connectionManager;
        this.echoHandler = echoHandler;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session){
        log.info("LLOneBot 连接建立: {}", session);

    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message){
        String payload = message.getPayload();

        // Check if it's an API response (Echo)
        if (echoHandler.handleEcho(payload)) {
            log.debug("收到echo消息: {}", payload);
            return;
        }

        try {
            Event event = EventParser.parse(payload);
            if (event instanceof HeartbeatEvent heartbeat) {
                connectionManager.updateHeartbeat(session, heartbeat.getInterval());
                return;
            }
            else if (event instanceof LifecycleEvent) {
                long selfId = event.getSelfId();
                session.getAttributes().put("selfId", selfId);
                connectionManager.registerSession(session);
                log.info("已连接QQ: {}", selfId);
                return;
            }

            log.info("收到消息: {}", payload);
            // Publish event for upper layers
            eventPublisher.publish(event);
            
        } catch (Exception e) {
            log.error("解析消息失败: {}", payload, e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status){
        log.info("QQ连接断开: {}", session.getAttributes().get("selfId"));
        connectionManager.removeSession(session);
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes){
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
