package org.mango.mangobot.manager.websocket;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class BotConnectionManager {

    private final Map<Long, BotSession> sessions = new ConcurrentHashMap<>();

    public void registerSession(WebSocketSession webSocketSession) {
        BotSession botSession = new BotSession(webSocketSession);
        sessions.put(getBotQQ(webSocketSession), botSession);
        log.debug("注册新连接: {}", getBotQQ(webSocketSession));
    }

    public void removeSession(WebSocketSession session) {
        BotSession removed = sessions.remove(getBotQQ(session));
        if (removed != null) {
            log.debug("移除连接: {}", getBotQQ(session));
        }
    }

    public BotSession getSession(Long sessionId) {
        return sessions.get(sessionId);
    }

    public void updateHeartbeat(WebSocketSession webSocketSession, long interval) {
        BotSession session = sessions.get(getBotQQ(webSocketSession));
        if (session != null) {
            session.updateHeartbeat();
            if (interval > 0) {
                session.setHeartbeatInterval(interval);
            }
        }
    }


    private Long getBotQQ(WebSocketSession session) {
        return (Long) session.getAttributes().get("selfId");
    }

    /**
     * Check for heartbeat timeouts every 10 seconds.
     * If a session hasn't sent a heartbeat within 3 * interval, close it.
     */
    @Scheduled(fixedRate = 10000)
    public void checkHeartbeats() {
        long now = System.currentTimeMillis();
        sessions.values().forEach(session -> {
            long timeout = session.getHeartbeatInterval() * 3;
            if (now - session.getLastHeartbeatTime() > timeout) {
                log.warn("Session {} 超时 (上次心跳在 {} ms 前). 关闭连接.",
                        session.getSessionId(), now - session.getLastHeartbeatTime());
                session.close();
                removeSession(session.getSession());
            }
        });
    }


    @Getter
    public static class BotSession {
        private final WebSocketSession session;
        private final String sessionId;

        @Setter
        private Long selfId;

        private long lastHeartbeatTime;

        @Setter
        private long heartbeatInterval = 60000; // Default 60s, updated by heartbeat event

        public BotSession(WebSocketSession session) {
            this.session = session;
            this.sessionId = session.getId();
            this.lastHeartbeatTime = System.currentTimeMillis();
        }

        public void updateHeartbeat() {
            this.lastHeartbeatTime = System.currentTimeMillis();
        }

        public boolean isConnected() {
            return session.isOpen();
        }

        public void close() {
            try {
                if (session.isOpen()) {
                    session.close();
                }
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}
