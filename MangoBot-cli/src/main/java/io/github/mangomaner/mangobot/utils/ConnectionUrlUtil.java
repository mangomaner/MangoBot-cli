package io.github.mangomaner.mangobot.utils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * OneBot WebSocket 服务器地址解析：url 是唯一输入，host/port/path 均从中派生。
 */
public final class ConnectionUrlUtil {

    public record Parsed(String host, int port, String path) {
    }

    private ConnectionUrlUtil() {
    }

    public static Parsed parse(String url) {
        String raw = url == null ? "" : url.trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("连接 URL 不能为空");
        }
        if (!raw.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            raw = "ws://" + raw;
        }
        try {
            URI uri = new URI(raw);
            if (uri.getHost() == null || uri.getHost().isEmpty()) {
                throw new IllegalArgumentException("连接 URL 缺少主机地址，示例: ws://0.0.0.0:8080/ws");
            }
            if (uri.getPort() < 0) {
                throw new IllegalArgumentException("连接 URL 缺少端口，示例: ws://0.0.0.0:8080/ws");
            }
            String path = uri.getPath();
            if (path == null) {
                path = "";
            }
            return new Parsed(uri.getHost(), uri.getPort(), path);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("连接 URL 格式不正确");
        }
    }

    public static String normalize(String url) {
        Parsed parsed = parse(url);
        return "ws://" + parsed.host() + ":" + parsed.port() + parsed.path();
    }
}
