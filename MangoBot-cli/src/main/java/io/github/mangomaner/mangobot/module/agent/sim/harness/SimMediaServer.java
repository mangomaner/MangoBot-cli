package io.github.mangomaner.mangobot.module.agent.sim.harness;

import com.sun.net.httpserver.HttpServer;
import io.github.mangomaner.mangobot.api.MangoFileApi;
import io.github.mangomaner.mangobot.module.file.model.domain.BotFiles;
import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模拟台的图片源：一个只在本机监听的小 HTTP 服务，按 fileId 把<b>真实图片字节</b>发出去。
 *
 * <p><b>为什么必须有它</b>：入站图片段只带 {@code url}，应用用 {@link java.net.http.HttpClient}
 * 去下载（{@code OneBotMessageFileProcessor}）——而 HttpClient 只认 http/https，{@code file://}
 * 不行。所以要让模型真的看到像素，就必须有一个能被下载的 URL。
 *
 * <p><b>为什么这事值得做</b>：不这么做，模型在模拟里只看到一个字面量 {@code [图片]}，
 * 看不到图里是什么——而生产里 {@code ChatTurnRunner} 是把真实字节当图片块送进模型的。
 * 环境不像生产，测出来的结论就不算数。顺带它还绕开了一个当下就坏着的东西：图片描述服务
 * （VLM）余额不足、返回 402，所以 {@code [图片#id: 描述]} 现在没有描述——但只要像素到了，
 * 模型不需要描述也看得见图。
 *
 * <p>只绑 127.0.0.1、端口由系统分配（0），不对外暴露。
 */
@Slf4j
public final class SimMediaServer {

    private final HttpServer server;
    private final int port;

    private SimMediaServer(HttpServer server, int port) {
        this.server = server;
        this.port = port;
    }

    /** 启动服务；端口由系统分配。 */
    public static SimMediaServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/img/", exchange -> {
            String raw = exchange.getRequestURI().getPath().substring("/img/".length());
            String fileId = URLDecoder.decode(raw, StandardCharsets.UTF_8);
            try {
                byte[] bytes = load(fileId);
                if (bytes == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                exchange.getResponseHeaders().add("Content-Type", contentTypeOf(fileId));
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (Exception e) {
                log.warn("模拟图片服务出错：fileId={}", fileId, e);
                try {
                    exchange.sendResponseHeaders(500, -1);
                } catch (IOException ignored) {
                    // 连接已经断了，没什么可做的
                }
            } finally {
                exchange.close();
            }
        });
        // 单线程够了：一场模拟里图片是逐条喂的
        server.setExecutor(runnable -> {
            Thread t = new Thread(runnable, "sim-media");
            t.setDaemon(true);
            t.start();
        });
        server.start();
        int port = server.getAddress().getPort();
        log.info("模拟图片服务已启动：http://127.0.0.1:{}/img/<fileId>", port);
        return new SimMediaServer(server, port);
    }

    /** 某个 fileId 的下载地址——填进出站帧的图片段 {@code url}。 */
    public String urlOf(String fileId) {
        return "http://127.0.0.1:" + port + "/img/" + fileId;
    }

    public void stop() {
        server.stop(0);
    }

    private static byte[] load(String fileId) throws IOException {
        if (fileId == null || fileId.isBlank()) {
            return null;
        }
        BotFiles file = MangoFileApi.getFileByFileId(fileId);
        if (file == null || file.getFilePath() == null || file.getFilePath().isBlank()) {
            log.warn("模拟图片服务：库里没有这个 fileId 或它没有本地路径：{}", fileId);
            return null;
        }
        Path path = FileUtils.resolvePath(file.getFilePath());
        if (!Files.isRegularFile(path)) {
            log.warn("模拟图片服务：本地文件不存在：{}", path);
            return null;
        }
        return Files.readAllBytes(path);
    }

    private static String contentTypeOf(String fileId) {
        String lower = fileId.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "image/jpeg";
    }
}
