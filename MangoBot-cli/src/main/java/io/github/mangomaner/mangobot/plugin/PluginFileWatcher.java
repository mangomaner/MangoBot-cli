package io.github.mangomaner.mangobot.plugin;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class PluginFileWatcher {

    private final PluginManager pluginManager;
    private Thread watcherThread;
    private volatile boolean running = false;
    // 用于防抖
    private final Map<Path, Long> lastEvents = new ConcurrentHashMap<>();

    public PluginFileWatcher(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @PostConstruct
    public void start() {
        running = true;
        watcherThread = new Thread(this::watch, "Plugin-Watcher-Thread");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
    }

    private void watch() {
        File pluginDir = pluginManager.getPluginDirectory();
        if (!pluginDir.exists()) {
            // 尝试等待目录创建
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}
            
            pluginDir = pluginManager.getPluginDirectory();
            if (!pluginDir.exists()) {
                log.warn("插件目录不存在，文件监听器未启动: {}", pluginDir.getAbsolutePath());
                return;
            }
        }

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            Path path = pluginDir.toPath();
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
            
            log.info("开始监听插件目录: {}", path);

            while (running) {
                WatchKey key;
                try {
                    key = watchService.poll(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (key == null) continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path fileName = ev.context();
                    
                    if (fileName.toString().endsWith(".jar")) {
                        handleFileChange(pluginDir, fileName);
                    }
                }

                if (!key.reset()) {
                    break;
                }
            }
        } catch (IOException e) {
            log.error("插件文件监听器出错", e);
        }
    }

    private void handleFileChange(File dir, Path fileName) {
        // 防抖：如果 2 秒内处理过，则跳过
        long now = System.currentTimeMillis();
        Long last = lastEvents.get(fileName);
        if (last != null && (now - last < 2000)) {
            return;
        }
        lastEvents.put(fileName, now);

        log.info("检测到插件文件变化: {}", fileName);
        
        // 启动新线程处理，避免阻塞 WatchService
        new Thread(() -> {
            // 延迟等待文件写入完成（防止文件锁）
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}

            File file = new File(dir, fileName.toString());
            String pluginId = file.getName();
            
            // 先尝试卸载旧版本
            pluginManager.unloadPlugin(pluginId);
            
            // 重试加载机制
            int retries = 3;
            while (retries > 0) {
                try {
                    pluginManager.loadPlugin(file);
                    break;
                } catch (Exception e) {
                    retries--;
                    if (retries == 0) {
                        log.error("加载插件 {} 失败", pluginId, e);
                    } else {
                        log.warn("加载插件 {} 失败，准备重试...", pluginId);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {}
                    }
                }
            }
        }).start();
    }
}
