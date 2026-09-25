package io.github.mangomaner.mangobot.adapter.onebot.handler.inbound.receive_websocket_message;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.adapter.WebSocketProtocolAdapter;
import io.github.mangomaner.mangobot.adapter.onebot.model.event.OneBotEvent;
import io.github.mangomaner.mangobot.events.onebot.meta.OneBotHeartbeatEvent;
import io.github.mangomaner.mangobot.events.onebot.meta.OneBotLifecycleEvent;
import io.github.mangomaner.mangobot.adapter.onebot.handler.echo.OneBotEchoHandler;
import io.github.mangomaner.mangobot.adapter.onebot.handler.inbound.json_to_event.OneBotEventParser;
import io.github.mangomaner.mangobot.infra.MangoEventPublisher;
import io.github.mangomaner.mangobot.infra.websocket.ConnectionSessionManager;
import io.github.mangomaner.mangobot.infra.websocket.model.ConnectionSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OneBotMessageHandler implements WebSocketProtocolAdapter {

    private static final String PROTOCOL_TYPE = "onebot_qq";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final MangoEventPublisher eventPublisher;
    private final ConnectionSessionManager sessionManager;
    private final OneBotEchoHandler echoHandler;

    public OneBotMessageHandler(MangoEventPublisher eventPublisher,
                                ConnectionSessionManager sessionManager,
                                OneBotEchoHandler echoHandler) {
        this.eventPublisher = eventPublisher;
        this.sessionManager = sessionManager;
        this.echoHandler = echoHandler;
    }

    @Override
    public String getProtocolType() {
        return PROTOCOL_TYPE;
    }

    @Override
    public void onMessage(ConnectionSession session, String message) {
        if (echoHandler.handleEcho(message)) {
            log.debug("收到 echo 消息: {}", message);
            return;
        }

        try {
            OneBotEvent event = OneBotEventParser.parse(message);

            if (event instanceof OneBotHeartbeatEvent heartbeat) {
                sessionManager.updateHeartbeat(session, heartbeat.getInterval());
                return;
            }

            if (event instanceof OneBotLifecycleEvent lifecycle) {
                long selfId = event.getSelfId();
                sessionManager.registerSelfId(session, selfId);
                log.info("OneBot 已连接: selfId={}, configId={}", selfId, session.getConfigId());
                return;
            }

            // 别在这里加 event == null 判断。反序列化器对没映射的 post_type 返回 null，
            // 于是在这一行抛 NPE、被下面 catch 成一条带原始报文的 ERROR——这是故意的：
            // 它是"有哪些事件类型还没被处理"的探针，静默跳过就等于把这个信号抹掉了。
            // 要收口的话先建好对应的事件类，别改成 return。
            log.debug("收到事件: {}", event.getClass().getSimpleName());
            eventPublisher.publish(event);
            
        } catch (Exception e) {
            log.error("解析消息失败: {}", message, e);
        }
    }

    @Override
    public void onConnect(ConnectionSession session) {
        log.info("OneBot 连接建立: {}", session.getRemoteAddress());
        sessionManager.registerSession(session);
    }

    @Override
    public void onDisconnect(ConnectionSession session) {
        log.info("OneBot 连接断开: selfId={}", session.getSelfId());
        sessionManager.removeSession(session);
    }
}
