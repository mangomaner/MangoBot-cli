package io.github.mangomaner.mangobot.manager.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import io.github.mangomaner.mangobot.model.onebot.api.OneBotApiResponse;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 负责处理 WebSocket 的 Echo 响应，实现异步转同步
 */
@Component
@Slf4j
public class EchoHandler {

    private final Map<String, CompletableFuture<OneBotApiResponse>> pendingRequests = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public EchoHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 注册一个等待中的请求
     * @param echo 请求的唯一标识
     * @return CompletableFuture 用于等待结果
     */
    public CompletableFuture<OneBotApiResponse> register(String echo) {
        CompletableFuture<OneBotApiResponse> future = new CompletableFuture<>();
        pendingRequests.put(echo, future);
        return future;
    }

    /**
     * 处理收到的 Echo 消息
     * @param jsonRaw 原始 JSON 字符串
     * @return true 如果是 Echo 消息并已处理，false 否则
     */
    public boolean handleEcho(String jsonRaw) {
        try {
            JsonNode node = objectMapper.readTree(jsonRaw);
            if (node.has("echo")) {
                String echo = node.get("echo").asText();
                CompletableFuture<OneBotApiResponse> future = pendingRequests.remove(echo);
                if (future != null) {
                    try {
                        OneBotApiResponse response = objectMapper.treeToValue(node, OneBotApiResponse.class);
                        future.complete(response);
                        return true;
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                        return true; // 即使解析失败也视为已处理，避免后续误判
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parse errors, let normal event parser handle it
        }
        return false;
    }

    /**
     * 等待响应，带超时控制
     */
    public OneBotApiResponse waitForResponse(String echo, long timeout, TimeUnit unit) {
        CompletableFuture<OneBotApiResponse> future = pendingRequests.get(echo);
        if (future == null) {
            throw new IllegalStateException("Echo " + echo + " 未注册");
        }
        try {
            return future.get(timeout, unit);
        } catch (TimeoutException e) {
            pendingRequests.remove(echo);
            throw new RuntimeException("等待 OneBot 响应超时: " + echo, e);
        } catch (Exception e) {
            pendingRequests.remove(echo);
            throw new RuntimeException("获取 OneBot 响应失败: " + echo, e);
        }
    }
}
