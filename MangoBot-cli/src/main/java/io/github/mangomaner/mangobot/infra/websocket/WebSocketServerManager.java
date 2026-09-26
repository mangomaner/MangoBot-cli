package io.github.mangomaner.mangobot.infra.websocket;

import io.github.mangomaner.mangobot.adapter.onebot.model.domain.OneBotConfig;
import io.github.mangomaner.mangobot.utils.ConnectionUrlUtil;
import io.github.mangomaner.mangobot.adapter.model.enums.ConnectionStatus;
import io.github.mangomaner.mangobot.system.common.ErrorCode;
import io.github.mangomaner.mangobot.infra.websocket.model.ConnectionSession;
import io.github.mangomaner.mangobot.system.exception.BusinessException;
import io.github.mangomaner.mangobot.system.mapper.connection.OneBotConfigMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class WebSocketServerManager {

    private static final int CLOSE_POLICY_VIOLATION = 1008;
    private static final Map<Long, ServerInstance> runningServers = new ConcurrentHashMap<>();

    private final MessageDispatcher messageDispatcher;
    private final OneBotConfigMapper oneBotConfigMapper;

    public WebSocketServerManager(MessageDispatcher messageDispatcher, OneBotConfigMapper oneBotConfigMapper) {
        this.messageDispatcher = messageDispatcher;
        this.oneBotConfigMapper = oneBotConfigMapper;
    }

    public synchronized void startServer(OneBotConfig config) throws Exception {
        Long configId = config.getId();
        
        if (runningServers.containsKey(configId)) {
            log.warn("WebSocket 服务器已在运行中，配置ID: {}", configId);
            return;
        }

        String url = config.getUrl();
        String protocolType = config.getProtocolType();

        if (protocolType ==  null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "协议类型不能为空");
        }

        log.info("正在启动 WebSocket 服务器: {}, 协议: {}", url, protocolType);

        ServerInstance instance = new ServerInstance(config, messageDispatcher, oneBotConfigMapper);
        instance.start();
        runningServers.put(configId, instance);

        log.info("WebSocket 服务器启动成功: {}, 协议: {}", url, protocolType);
    }

    public synchronized void stopServer(Long configId) {
        ServerInstance instance = runningServers.remove(configId);
        if (instance != null) {
            try {
                instance.stop();
                log.info("WebSocket 服务器已停止，配置ID: {}", configId);
            } catch (Exception e) {
                log.error("停止 WebSocket 服务器失败，配置ID: {}", configId, e);
            }
        }
    }

    public boolean isRunning(Long configId) {
        ServerInstance instance = runningServers.get(configId);
        return instance != null && instance.isRunning();
    }

    public ConnectionStatus getStatus(Long configId) {
        ServerInstance instance = runningServers.get(configId);
        if (instance == null) {
            return ConnectionStatus.STOPPED;
        }
        return instance.isRunning() ? ConnectionStatus.RUNNING : ConnectionStatus.ERROR;
    }

    public void stopAll() {
        runningServers.keySet().forEach(this::stopServer);
    }

    @Getter
    private static class ServerInstance {
        private final OneBotConfig config;
        private final ConnectionUrlUtil.Parsed connectionUrl;
        private final WebSocketServer server;
        private final MessageDispatcher messageDispatcher;
        private final OneBotConfigMapper oneBotConfigMapper;
        private final Map<WebSocket, ConnectionSession> sessions = new ConcurrentHashMap<>();
        private volatile boolean running = false;

        public ServerInstance(OneBotConfig config, MessageDispatcher messageDispatcher, OneBotConfigMapper oneBotConfigMapper) {
            this.config = config;
            this.connectionUrl = ConnectionUrlUtil.parse(config.getUrl());
            this.messageDispatcher = messageDispatcher;
            this.oneBotConfigMapper = oneBotConfigMapper;
            this.server = createWebSocketServer(config);
        }

        private WebSocketServer createWebSocketServer(OneBotConfig config) {
            InetSocketAddress address = new InetSocketAddress(connectionUrl.host(), connectionUrl.port());
            String protocolType = config.getProtocolType() != null ? config.getProtocolType() : "onebot_qq";
            
            return new WebSocketServer(address) {

                @Override
                public void onOpen(WebSocket conn, ClientHandshake handshake) {
                    log.info("WebSocket 连接建立: {}", conn.getRemoteSocketAddress());

                    String expectedToken = getConfiguredToken();
                    if (expectedToken != null && !expectedToken.isEmpty()) {
                        if (!validateToken(handshake, expectedToken)) {
                            log.warn("Token 验证失败，关闭连接: {}", conn.getRemoteSocketAddress());
                            conn.close(CLOSE_POLICY_VIOLATION, "Invalid token");
                            return;
                        }
                    }

                    String configuredPath = connectionUrl.path();
                    if (configuredPath != null && !configuredPath.trim().isEmpty() && !"/".equals(configuredPath.trim())) {
                        String requestPath = extractRequestPath(handshake.getResourceDescriptor());
                        if (!normalizePath(configuredPath).equals(normalizePath(requestPath))) {
                            log.warn("路径验证失败，关闭连接: {}，期望路径: {}", conn.getRemoteSocketAddress(), configuredPath);
                            conn.close(CLOSE_POLICY_VIOLATION, "Invalid path");
                            return;
                        }
                    }

                    ConnectionSession session = new ConnectionSession(conn, config.getId(), protocolType);
                    sessions.put(conn, session);
                    messageDispatcher.onConnect(session);
                }

                @Override
                public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                    log.info("WebSocket 连接关闭: {}, code: {}, reason: {}", 
                            conn.getRemoteSocketAddress(), code, reason);
                    
                    ConnectionSession session = sessions.remove(conn);
                    if (session != null) {
                        messageDispatcher.onDisconnect(session);
                    }
                }

                @Override
                public void onMessage(WebSocket conn, String message) {
                    log.debug("收到 WebSocket 消息: {}", message);
                    
                    ConnectionSession session = sessions.get(conn);
                    if (session != null) {
                        messageDispatcher.dispatch(session, message);
                    }
                }

                @Override
                public void onError(WebSocket conn, Exception ex) {
                    log.error("WebSocket 错误: {}", conn != null ? conn.getRemoteSocketAddress() : "unknown", ex);
                }

                @Override
                public void onStart() {
                    log.info("WebSocket 服务器启动完成: {}:{}", connectionUrl.host(), connectionUrl.port());
                    running = true;
                }

                private String getConfiguredToken() {
                    OneBotConfig latest = oneBotConfigMapper.selectById(config.getId());
                    return latest != null ? latest.getToken() : config.getToken();
                }

                private boolean validateToken(ClientHandshake handshake, String expectedToken) {
                    String authHeader = getAuthorizationHeader(handshake);
                    if (authHeader != null) {
                        String headerToken = parseTokenFromAuthorizationHeader(authHeader);
                        if (expectedToken.equals(headerToken)) {
                            return true;
                        }
                    }

                    String resourceDescriptor = handshake.getResourceDescriptor();
                    String queryToken = parseAccessTokenParam(resourceDescriptor);
                    if (expectedToken.equals(queryToken)) {
                        return true;
                    }

                    if (resourceDescriptor != null && resourceDescriptor.endsWith("/" + expectedToken)) {
                        return true;
                    }

                    return false;
                }

                private String getAuthorizationHeader(ClientHandshake handshake) {
                    Iterator<String> fields = handshake.iterateHttpFields();
                    while (fields.hasNext()) {
                        String name = fields.next();
                        if ("authorization".equalsIgnoreCase(name)) {
                            return handshake.getFieldValue(name);
                        }
                    }
                    return null;
                }

                private String parseTokenFromAuthorizationHeader(String header) {
                    String trimmed = header.trim();
                    String lower = trimmed.toLowerCase();
                    if (lower.startsWith("bearer ")) {
                        return trimmed.substring(7).trim();
                    }
                    if (lower.startsWith("token ")) {
                        return trimmed.substring(6).trim();
                    }
                    return trimmed;
                }

                private String parseAccessTokenParam(String resourceDescriptor) {
                    if (resourceDescriptor == null) {
                        return null;
                    }
                    int queryIndex = resourceDescriptor.indexOf('?');
                    if (queryIndex < 0) {
                        return null;
                    }
                    String query = resourceDescriptor.substring(queryIndex + 1);
                    for (String pair : query.split("&")) {
                        String[] kv = pair.split("=", 2);
                        if (kv.length == 2 && "access_token".equals(kv[0])) {
                            try {
                                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                            } catch (Exception e) {
                                return kv[1];
                            }
                        }
                    }
                    return null;
                }

                private String extractRequestPath(String resourceDescriptor) {
                    if (resourceDescriptor == null) {
                        return null;
                    }
                    int queryIndex = resourceDescriptor.indexOf('?');
                    return queryIndex >= 0 ? resourceDescriptor.substring(0, queryIndex) : resourceDescriptor;
                }

                private String normalizePath(String path) {
                    if (path == null || path.isEmpty()) {
                        return "/";
                    }
                    String p = path.startsWith("/") ? path : "/" + path;
                    while (p.length() > 1 && p.endsWith("/")) {
                        p = p.substring(0, p.length() - 1);
                    }
                    return p;
                }
            };
        }

        public void start() throws Exception {
            server.start();
        }

        public void stop() throws Exception {
            running = false;
            server.stop();
        }

        public boolean isRunning() {
            return running;
        }
    }
}
