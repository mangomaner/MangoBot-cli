package io.github.mangomaner.mangobot.controller;

import io.github.mangomaner.mangobot.plugin.PluginManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plugin")
@Slf4j
public class PluginController {

    @Resource
    private PluginManager pluginManager;

    /**
     * 获取所有已加载的插件列表
     */
    @GetMapping("/list")
    public Map<String, Object> listPlugins() {
        List<String> pluginIds = pluginManager.getLoadedPluginIds();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", pluginIds);
        result.put("total", pluginIds.size());
        return result;
    }

    /**
     * 卸载指定插件
     * @param pluginId 插件文件名，例如 example.jar
     */
    @PostMapping("/unload")
    public Map<String, Object> unloadPlugin(@RequestParam String pluginId) {
        log.info("收到卸载插件请求: {}", pluginId);
        pluginManager.unloadPlugin(pluginId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "插件卸载操作已执行");
        return result;
    }

    /**
     * 手动加载/重载插件
     * @param pluginId 插件文件名，例如 example.jar
     */
    @PostMapping("/load")
    public Map<String, Object> loadPlugin(@RequestParam String pluginId) {
        log.info("收到加载插件请求: {}", pluginId);
        File pluginFile = new File(pluginManager.getPluginDirectory(), pluginId);
        
        Map<String, Object> result = new HashMap<>();
        if (!pluginFile.exists()) {
            result.put("code", 404);
            result.put("message", "插件文件不存在");
            return result;
        }

        // 先尝试卸载（如果是重载）
        pluginManager.unloadPlugin(pluginId);
        pluginManager.loadPlugin(pluginFile);
        
        result.put("code", 200);
        result.put("message", "插件加载操作已执行");
        return result;
    }
}
