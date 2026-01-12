package org.mango.mangobot.manager.websocket;

import lombok.extern.slf4j.Slf4j;
import org.mango.mangobot.model.onebot.event.Event;
import org.mango.mangobot.model.onebot.event.EventParser;
import org.mango.mangobot.model.onebot.event.meta.HeartbeatEvent;
import org.mango.mangobot.model.onebot.event.meta.LifecycleEvent;
import org.springframework.context.ApplicationEventPublisher;
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

    private final ApplicationEventPublisher eventPublisher;
    private final BotConnectionManager connectionManager;

    public BotMessageHandler(ApplicationEventPublisher eventPublisher, BotConnectionManager connectionManager) {
        this.eventPublisher = eventPublisher;
        this.connectionManager = connectionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("LLOneBot 连接建立: {}", session.getId());
        connectionManager.registerSession(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("收到消息: {}", payload);

        try {
            Event event = EventParser.parse(payload);

            if (event instanceof HeartbeatEvent) {
                HeartbeatEvent heartbeat = (HeartbeatEvent) event;
                connectionManager.updateHeartbeat(session.getId(), heartbeat.getInterval());
            }
            else if (event instanceof LifecycleEvent) {
                long selfId = event.getSelfId();
                connectionManager.updateBotId(session.getId(), selfId);
                log.info("已连接QQ: {}", selfId);
            }

            eventPublisher.publishEvent(event);
            
        } catch (Exception e) {
            log.error("解析消息失败: {}", payload, e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("QQ连接断开: {}", session.getId());
        connectionManager.removeSession(session.getId());
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
