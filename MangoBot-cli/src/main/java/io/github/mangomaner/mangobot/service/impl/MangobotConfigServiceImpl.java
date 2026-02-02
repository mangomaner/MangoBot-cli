package io.github.mangomaner.mangobot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.mangomaner.mangobot.manager.GlobalConfigCache;
import io.github.mangomaner.mangobot.manager.event.ConfigChangeEvent;
import io.github.mangomaner.mangobot.manager.event.MangoEventPublisher;
import io.github.mangomaner.mangobot.model.domain.MangobotConfig;
import io.github.mangomaner.mangobot.model.dto.config.CreateConfigRequest;
import io.github.mangomaner.mangobot.model.dto.config.UpdateConfigByKeyRequest;
import io.github.mangomaner.mangobot.model.dto.config.UpdateConfigRequest;
import io.github.mangomaner.mangobot.model.vo.ConfigVO;
import io.github.mangomaner.mangobot.service.MangobotConfigService;
import io.github.mangomaner.mangobot.mapper.MangobotConfigMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
* @author mangoman
* @description 针对表【mangobot_config】的数据库操作Service实现
* @createDate 2026-01-17 13:11:01
*/
@Service
@Slf4j
public class MangobotConfigServiceImpl extends ServiceImpl<MangobotConfigMapper, MangobotConfig>
    implements MangobotConfigService{

    @Resource
    private GlobalConfigCache globalConfigCache;

    @Resource
    private MangoEventPublisher mangoEventPublisher;

    @PostConstruct
    public void init() {
        refreshCache();
    }

    /**
     * 定时任务：每小时刷新一次配置缓存
     * 作为兜底策略，确保缓存与数据库的最终一致性
     */
    @Scheduled(fixedRate = 3600000) // 1小时 = 60 * 60 * 1000 毫秒
    public void refreshCache() {
        try {
            List<MangobotConfig> list = this.list();
            Map<String, String> map = new HashMap<>();
            for (MangobotConfig config : list) {
                map.put(config.getConfigKey(), config.getConfigValue());
            }
            globalConfigCache.refreshAll(map);
            log.info("配置缓存已同步，当前加载 {} 项配置", map.size());
        } catch (Exception e) {
            log.error("定时刷新配置缓存失败", e);
        }
    }

    @Override
    public void registeConfig(CreateConfigRequest  request) {
        if (request.getKey() == null || !request.getKey().startsWith("plugin")) {
            log.error("key格式错误，请以 plugin 开头，详情参照开发文档");
            return;
        }

        MangobotConfig config = new MangobotConfig();
        config.setPluginId(request.getPluginId());
        config.setConfigKey(request.getKey());
        config.setConfigValue(request.getValue());
        config.setConfigType(request.getType());
        config.setDescription(request.getDesc());
        config.setExplain(request.getExplain());
        this.save(config);

        // 更新缓存并发布事件
        globalConfigCache.put(config.getConfigKey(), config.getConfigValue());
        mangoEventPublisher.publish(new ConfigChangeEvent(config.getConfigKey(), config.getConfigValue()));
    }

    @Override
    public void deleteByPluginId(Long pluginId) {
        if (pluginId == null) {
            return;
        }
        LambdaQueryWrapper<MangobotConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MangobotConfig::getPluginId, pluginId);
        List<MangobotConfig> configs = this.list(wrapper);
        
        this.remove(wrapper);

        // 更新缓存并发布事件
        for (MangobotConfig config : configs) {
            globalConfigCache.remove(config.getConfigKey());
            // 删除时发布值为 null 的事件，或者定义特殊的删除事件。这里发布 null 表示删除/置空
            mangoEventPublisher.publish(new ConfigChangeEvent(config.getConfigKey(), null));
        }
    }

    @Override
    public List<ConfigVO> getAllConfigs() {
        List<MangobotConfig> configs = this.list();
        return configs.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public ConfigVO getConfigByKey(String configKey) {
        if (configKey == null || (!configKey.startsWith("main") && !configKey.startsWith("plugin"))) {
            log.error("key格式错误，请以 plugin/main 开头，详情参照开发文档");
            return null;
        }

        // 优先从缓存获取，如果没有再查库（虽然缓存应该总是有）
        String cachedValue = globalConfigCache.get(configKey);
        if (cachedValue != null) {
            ConfigVO vo = new ConfigVO();
            vo.setConfigKey(configKey);
            vo.setConfigValue(cachedValue);
            return vo;
        }

        LambdaQueryWrapper<MangobotConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MangobotConfig::getConfigKey, configKey);
        MangobotConfig config = this.getOne(wrapper);
        return convertToVO(config);
    }

    @Override
    public ConfigVO getConfigById(Long id) {
        MangobotConfig config = this.getById(id);
        return convertToVO(config);
    }

    @Override
    public Boolean updateConfigByKey(UpdateConfigByKeyRequest request) {
        if (request.getConfigKey() == null || (!request.getConfigKey().startsWith("plugin") && !request.getConfigKey().startsWith("main"))) {
            log.error("key格式错误，请以 plugin/main 开头，详情参照开发文档");
            return false;
        }
        LambdaQueryWrapper<MangobotConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MangobotConfig::getConfigKey, request.getConfigKey());
        MangobotConfig config = this.getOne(wrapper);
        if (config == null) {
            return false;
        }
        config.setConfigValue(request.getConfigValue());
        boolean success = this.updateById(config);
        
        if (success) {
            globalConfigCache.put(config.getConfigKey(), config.getConfigValue());
            mangoEventPublisher.publish(new ConfigChangeEvent(config.getConfigKey(), config.getConfigValue()));
        }
        return success;
    }

    @Override
    public Boolean updateConfigById(UpdateConfigRequest request) {
        MangobotConfig config = this.getById(request.getId());
        if (config == null) {
            return false;
        }
        config.setConfigValue(request.getConfigValue());
        boolean success = this.updateById(config);

        if (success) {
            globalConfigCache.put(config.getConfigKey(), config.getConfigValue());
            mangoEventPublisher.publish(new ConfigChangeEvent(config.getConfigKey(), config.getConfigValue()));
        }
        return success;
    }

    private ConfigVO convertToVO(MangobotConfig config) {
        if (config == null) {
            return null;
        }
        ConfigVO vo = new ConfigVO();
        BeanUtils.copyProperties(config, vo);
        return vo;
    }
}
