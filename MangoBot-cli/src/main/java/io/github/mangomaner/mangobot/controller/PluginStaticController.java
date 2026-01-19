package io.github.mangomaner.mangobot.controller;

import io.github.mangomaner.mangobot.utils.FileUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/static")
public class PluginStaticController {

    @GetMapping("/{pluginName}/**")
    public ResponseEntity<Resource> getPluginStaticResource(
            @PathVariable String pluginName,
            HttpServletRequest request) {

        try {
            String requestUri = request.getRequestURI();
            String prefix = "/static/" + pluginName + "/";
            String relativePath = "";

            if (requestUri.startsWith(prefix)) {
                relativePath = requestUri.substring(prefix.length());
            }

            // 基础 web 目录
            Path webBaseDir = FileUtils.getBaseDirectory().resolve("web");

            // 安全解析目标文件路径
            Path targetFile;
            try {
                targetFile = FileUtils.resolvePath(webBaseDir, Paths.get(pluginName, relativePath).toString());
            } catch (SecurityException e) {
                return ResponseEntity.badRequest().build();
            }

            Resource resource = null;
            String contentType = "application/octet-stream";

            // 1. 尝试加载请求的文件
            if (Files.exists(targetFile) && Files.isRegularFile(targetFile)) {
                resource = new UrlResource(targetFile.toUri());
                contentType = detectMimeType(targetFile);
            } else {
                // 2. Fallback: 加载 index.html（SPA 支持）
                try {
                    Path indexFile = FileUtils.resolvePath(webBaseDir, pluginName + "/index.html");
                    if (Files.exists(indexFile) && Files.isRegularFile(indexFile)) {
                        resource = new UrlResource(indexFile.toUri());
                        contentType = "text/html;charset=utf-8"; // 强制 HTML 类型
                    }
                } catch (SecurityException ignored) {
                    // 忽略安全异常，不 fallback
                }
            }

            if (resource == null || !resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            // 可选：记录异常日志
            // e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // MIME 类型探测（优先基于扩展名）
    private String detectMimeType(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "text/html;charset=utf-8";
        } else if (fileName.endsWith(".js")) {
            return "application/javascript";
        } else if (fileName.endsWith(".css")) {
            return "text/css";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (fileName.endsWith(".ico")) {
            return "image/x-icon";
        } else if (fileName.endsWith(".json")) {
            return "application/json";
        } else if (fileName.endsWith(".txt")) {
            return "text/plain";
        } else if (fileName.endsWith(".woff") || fileName.endsWith(".woff2")) {
            return "font/woff2";
        }

        // fallback to system probe
        try {
            String type = Files.probeContentType(filePath);
            return (type != null && !type.isBlank()) ? type : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }
}