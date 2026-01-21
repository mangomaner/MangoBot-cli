package io.github.mangomaner.mangobot.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.mangomaner.mangobot.common.BaseResponse;
import io.github.mangomaner.mangobot.common.ResultUtils;
import io.github.mangomaner.mangobot.model.domain.Plugins;
import io.github.mangomaner.mangobot.model.plugin.PluginInfo;
import io.github.mangomaner.mangobot.plugin.PluginManager;
import io.github.mangomaner.mangobot.service.PluginsService;
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

    @Resource
    private PluginsService pluginsService;

    /**
     * 获取所有已加载的插件列表
     */
    @GetMapping("/list")
    public BaseResponse<List<PluginInfo>> listPlugins() {
        List<PluginInfo> pluginsInfo = pluginManager.getAllPluginsInfo();
        return ResultUtils.success(pluginsInfo);
    }

    /**
     * 停用插件 (Unload / Disable)
     * @param pluginId 插件文件名，例如 example.jar
     */
    @PostMapping("/unload")
    public BaseResponse<Void> unloadPlugin(@RequestParam String pluginId) {
        log.info("收到停用插件请求: {}", pluginId);
        // 更新数据库状态
        LambdaUpdateWrapper<Plugins> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Plugins::getJarName, pluginId).set(Plugins::getEnabled, 0);
        pluginsService.update(updateWrapper);

        pluginManager.unloadPlugin(pluginId);
        return ResultUtils.success(null);
    }

    /**
     * 启用/加载插件 (Load / Enable)
     * @param pluginId 插件文件名，例如 example.jar
     */
    @PostMapping("/load")
    public BaseResponse<Void> loadPlugin(@RequestParam String pluginId) {
        log.info("收到启用插件请求: {}", pluginId);
        File pluginFile = new File(pluginManager.getPluginDirectory(), pluginId);
        
        if (!pluginFile.exists()) {
            return ResultUtils.error(404, "插件文件不存在");
        }

        // 更新数据库状态
        LambdaUpdateWrapper<Plugins> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Plugins::getJarName, pluginId).set(Plugins::getEnabled, 1);
        pluginsService.update(updateWrapper);

        // 先尝试卸载（如果是重载）
        pluginManager.unloadPlugin(pluginId);
        pluginManager.loadPlugin(pluginFile);
        
        return ResultUtils.success(null);
    }

    /**
     * 彻底卸载插件 (Delete / Uninstall)
     * @param pluginId 插件文件名
     */
    @PostMapping("/uninstall")
    public BaseResponse<Void> uninstallPlugin(@RequestParam String pluginId) {
        log.info("收到卸载插件请求: {}", pluginId);
        pluginManager.uninstallPlugin(pluginId);
        return ResultUtils.success(null);
    }
}
