package org.mango.mangobot.manager.websocketReverseProxy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.mango.mangobot.manager.websocketReverseProxy.handler.MessageReflect;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class BotMessageHandler extends TextWebSocketHandler implements HandshakeInterceptor {

    @Resource
    private Map<String, WebSocketSession> sessionMap;
    @Resource
    private MessageReflect messageReflect;
    @Resource
    private Set<String> groupSet;
    private Map<String, Long> lastHeartbeatTimes = new HashMap<>();
    private static final long HEARTBEAT_INTERVAL_MS = 60_000; // 心跳间隔时间，单位为毫秒
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ObjectMapper objectMapper = new ObjectMapper();

    {
        // 启动全局心跳检查任务
        scheduler.scheduleAtFixedRate(this::checkHeartbeats, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        log.info("已启动全局心跳检测任务");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 此时还没有 self_id，等待 lifecycle connect 消息
        log.info("LLOneBot 连接成功: {}", session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();


        Map<String, Object> messageMap;
        try {
            // 将 JSON 字符串转为 Map
            messageMap = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("消息解析为Map失败");
            return;
        }

        if (messageMap == null) {
            log.error("解析后 messageMap 为空");
            return;
        }
        log.info("收到消息: " + payload);
        // 开启后仅接收群消息
        if(!"meta_event".equals(messageMap.getOrDefault("post_type", ""))){
            if(messageMap.containsKey("echo")){
                messageReflect.handleEchoEvent(messageMap);
            }
            if(!messageMap.containsKey("group_id")){
                return;
            }
            // 开启后仅接收groupSet中存储的群号的消息
            if(!groupSet.contains(messageMap.get("group_id").toString())){
                return;
            }
        }

        // 提取 post_type
        Optional.ofNullable(messageMap.get("post_type"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .ifPresent(postType -> {
                    switch (postType) {
                        case "meta_event" -> handleMetaEvent(session, messageMap);
                        case "notice" -> messageReflect.handleNoticeEvent(messageMap); // 处理通知事件
                        case "message", "message_sent" -> messageReflect.handleMessageEvent(messageMap);
                        default -> log.error("未处理的消息类型: " + postType);
                    }
                });
    }

    private void handleMetaEvent(WebSocketSession session, Map<String, Object> messageMap) {
        Optional.ofNullable(messageMap.get("meta_event_type"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .ifPresent(metaEventType -> {
                    if ("lifecycle".equals(metaEventType)) {
                        handleLifecycleMessage(session, messageMap);
                    } else if ("heartbeat".equals(metaEventType)) {
                        updateHeartbeatTime(messageMap);
                    }
                });
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Object selfIdObject = session.getAttributes().get("self_id");
        if (selfIdObject instanceof String) {
            String selfId = (String) selfIdObject;
            sessionMap.remove(selfId);
            lastHeartbeatTimes.remove(selfId);
            log.info("QQ号 {} 断开连接", selfId);
        } else {
            log.error("无法从会话属性中获取 self_id（Bot QQ号）");
        }
    }
    /**
     * 全局心跳检测任务
     */
    public void checkHeartbeats() {
        long currentTime = Instant.now().toEpochMilli();
        for (Map.Entry<String, Long> entry : lastHeartbeatTimes.entrySet()) {
            String selfId = entry.getKey();
            long lastTime = entry.getValue();
            if (currentTime - lastTime > HEARTBEAT_INTERVAL_MS * 2) {
                WebSocketSession session = sessionMap.get(selfId);
                if (session != null && session.isOpen()) {
                    try {
                        session.close();
                        log.warn("长时间未收到心跳包，断开连接: {}", selfId);
                    } catch (IOException e) {
                        log.error("断开连接时发生错误", e);
                    }
                }
                sessionMap.remove(selfId);
                lastHeartbeatTimes.remove(selfId);
            }
        }
    }

    /**
     * 处理生命周期事件，提取 self_id 并注册会话
     */
    private void handleLifecycleMessage(WebSocketSession session, Map<String, Object> messageMap) {
        try {
            Object selfIdObj = messageMap.get("self_id");
            String selfId = null;
            if(selfIdObj != null)
                selfId = selfIdObj.toString();

            if (sessionMap.containsKey(selfId)) {
                log.warn("QQ号 {} 已存在，可能重复连接", selfId);
                WebSocketSession oldSession = sessionMap.get(selfId);
                if (oldSession != null && oldSession.isOpen()) {
                    oldSession.close();
                }
            }

            sessionMap.put(selfId, session);
            lastHeartbeatTimes.put(selfId, Instant.now().toEpochMilli());

            // 将 selfId 存入 session 的 attributes 中
            session.getAttributes().put("self_id", selfId);

            log.info("QQ号 {} 已连接并注册", selfId);

            // 防止scheduler意外关闭
            if(scheduler.isShutdown()){
                scheduler.scheduleAtFixedRate(this::checkHeartbeats, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
                log.info("已启动全局心跳检测任务");
            }
        } catch (Exception e) {
            log.error("处理生命周期消息失败", e);
        }
    }

    /**
     * 更新指定 QQ 号的心跳时间
     */
    private void updateHeartbeatTime(Map<String, Object> messageMap) {
        try {
            Object selfIdObj = messageMap.get("self_id");
            String selfId = null;
            if(selfIdObj != null)
                selfId = selfIdObj.toString();
            if (lastHeartbeatTimes.containsKey(selfId)) {
                lastHeartbeatTimes.put(selfId, Instant.now().toEpochMilli());
                log.debug("更新QQ号 {} 的心跳时间", selfId);
            }
        } catch (Exception e) {
            log.error("更新心跳时间失败", e);
        }
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // 在握手之前执行的操作
        // 可以根据请求信息决定是否允许握手继续进行
        return true; // 返回 false 将阻止握手
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 在握手之后执行的操作
    }
}