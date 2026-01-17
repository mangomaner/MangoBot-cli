package io.github.mangomaner.mangobot.controller;

import io.github.mangomaner.mangobot.common.BaseResponse;
import io.github.mangomaner.mangobot.common.ResultUtils;
import io.github.mangomaner.mangobot.model.plugin.PluginInfo;
import io.github.mangomaner.mangobot.plugin.PluginManager;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;

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
    public BaseResponse<List<PluginInfo>> listPlugins() {
        List<PluginInfo> pluginsInfo = pluginManager.getLoadedPluginsInfo();
        return ResultUtils.success(pluginsInfo);
    }

    /**
     * 卸载指定插件
     * @param pluginId 插件文件名，例如 example.jar
     */
    @PostMapping("/unload")
    public BaseResponse<Void> unloadPlugin(@RequestParam String pluginId) {
        log.info("收到卸载插件请求: {}", pluginId);
        pluginManager.unloadPlugin(pluginId);
        return ResultUtils.success(null);
    }

    /**
     * 手动加载/重载插件
     * @param pluginId 插件文件名，例如 example.jar
     */
    @PostMapping("/load")
    public BaseResponse<Void> loadPlugin(@RequestParam String pluginId) {
        log.info("收到加载插件请求: {}", pluginId);
        File pluginFile = new File(pluginManager.getPluginDirectory(), pluginId);
        
        if (!pluginFile.exists()) {
            return ResultUtils.error(404, "插件文件不存在");
        }

        // 先尝试卸载（如果是重载）
        pluginManager.unloadPlugin(pluginId);
        pluginManager.loadPlugin(pluginFile);
        
        return ResultUtils.success(null);
    }
}
