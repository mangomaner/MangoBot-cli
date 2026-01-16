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

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/static/plugin")
public class PluginStaticController {

    @GetMapping("/{pluginName}/**")
    public ResponseEntity<Resource> getPluginStaticResource(
            @PathVariable String pluginName,
            HttpServletRequest request) {

        try {
            // 1. 获取请求路径后缀
            // request.getRequestURI() 返回完整路径，如 /static/plugin/demo/assets/app.js
            String requestUri = request.getRequestURI();
            // 注意：这里需要解码，防止中文路径问题，但 getRequestURI 通常已解码或未解码，
            // 若包含 URL 编码字符，可能需要 decode。但在 Spring Boot 中通常可以直接使用。
            // 简单起见，这里默认无需额外 decode，或者由 Path 处理。
            
            String prefix = "/static/plugin/" + pluginName + "/";
            String relativePath = "";
            // 防止路径匹配问题
            int index = requestUri.indexOf(prefix);
            if (index != -1) {
                relativePath = requestUri.substring(index + prefix.length());
            }

            // 2. 定位目标文件
            // 基础目录: base/web
            Path webBaseDir = FileUtils.getBaseDirectory().resolve("web");
            
            Path targetFile;
            try {
                // 安全解析路径，确保在 webBaseDir 下
                targetFile = FileUtils.resolvePath(webBaseDir, pluginName + "/" + relativePath);
            } catch (SecurityException e) {
                return ResponseEntity.badRequest().build();
            }

            Resource resource = null;

            // 3. 尝试读取文件
            if (Files.exists(targetFile) && Files.isRegularFile(targetFile)) {
                resource = new UrlResource(targetFile.toUri());
            } else {
                // 4. Fallback 逻辑 (SPA支持)
                // 如果找不到文件，尝试返回 index.html
                try {
                    // index.html 也应该在 pluginName 目录下
                    Path indexFile = FileUtils.resolvePath(webBaseDir, pluginName + "/index.html");
                    if (Files.exists(indexFile)) {
                        resource = new UrlResource(indexFile.toUri());
                    }
                } catch (SecurityException ignored) {
                    // 忽略异常，不做 fallback
                }
            }

            if (resource == null || !resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            // 5. 确定 Content-Type
            String contentType = null;
            try {
                Path resourcePath = Paths.get(resource.getURI());
                contentType = Files.probeContentType(resourcePath);
            } catch (Exception ignored) {
            }
            
            if (contentType == null) {
                // 默认类型
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
