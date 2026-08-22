package io.github.mangomaner.mangobot.module.configuration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.module.configuration.enums.ConfigType;
import io.github.mangomaner.mangobot.events.configuration.BotConfigChangedEvent;
import io.github.mangomaner.mangobot.module.configuration.model.config.ConfigMetadata;
import io.github.mangomaner.mangobot.module.configuration.util.ConfigTypeHandler;
import io.github.mangomaner.mangobot.system.mapper.configuration.BotConfigMapper;
import io.github.mangomaner.mangobot.module.configuration.model.domain.BotConfig;
import io.github.mangomaner.mangobot.module.configuration.model.dto.bot.CreateBotConfigRequest;
import io.github.mangomaner.mangobot.module.configuration.model.dto.bot.UpdateBotConfigRequest;
import io.github.mangomaner.mangobot.module.configuration.model.vo.BotConfigVO;
import io.github.mangomaner.mangobot.module.configuration.service.BotConfigService;
import io.github.mangomaner.mangobot.infra.MangoEventPublisher;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BotConfigServiceImpl extends ServiceImpl<BotConfigMapper, BotConfig>
        implements BotConfigService {

    @Resource
    private MangoEventPublisher mangoEventPublisher;

    @Resource
    private ConfigTypeHandler configTypeHandler;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public List<BotConfigVO> getAllConfigs() {
        LambdaQueryWrapper<BotConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(BotConfig::getBotId);
        return this.list(wrapper).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<BotConfigVO> getConfigsByBotId(String botId) {
        if (botId == null) {
            return getAllConfigs();
        }
        
        List<BotConfigVO> result = new ArrayList<>();
        
        LambdaQueryWrapper<BotConfig> defaultWrapper = new LambdaQueryWrapper<>();
        defaultWrapper.isNull(BotConfig::getBotId);
        List<BotConfig> defaultConfigs = this.list(defaultWrapper);
        
        LambdaQueryWrapper<BotConfig> botWrapper = new LambdaQueryWrapper<>();
        botWrapper.eq(BotConfig::getBotId, botId);
        List<BotConfig> botConfigs = this.list(botWrapper);
        
        Map<String, BotConfig> botConfigMap = botConfigs.stream()
                .collect(Collectors.toMap(BotConfig::getConfigKey, c -> c));
        
        for (BotConfig defaultConfig : defaultConfigs) {
            BotConfigVO vo = convertToVO(defaultConfig);
            if (botConfigMap.containsKey(defaultConfig.getConfigKey())) {
                BotConfig botConfig = botConfigMap.get(defaultConfig.getConfigKey());
                vo = convertToVO(botConfig);
            }
            result.add(vo);
        }
        
        return result;
    }

    @Override
    public List<BotConfigVO> getConfigsByCategory(String category) {
        LambdaQueryWrapper<BotConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotConfig::getCategory, category)
               .isNull(BotConfig::getBotId);
        return this.list(wrapper).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public BotConfigVO getConfigByKeyAndBotId(String configKey, String botId) {
        if (botId != null) {
            LambdaQueryWrapper<BotConfig> botWrapper = new LambdaQueryWrapper<>();
            botWrapper.eq(BotConfig::getConfigKey, configKey)
                      .eq(BotConfig::getBotId, botId);
            BotConfig botConfig = this.getOne(botWrapper);
            if (botConfig != null) {
                return convertToVO(botConfig);
            }
        }
        
        LambdaQueryWrapper<BotConfig> defaultWrapper = new LambdaQueryWrapper<>();
        defaultWrapper.eq(BotConfig::getConfigKey, configKey)
                      .isNull(BotConfig::getBotId);
        BotConfig defaultConfig = this.getOne(defaultWrapper);
        return defaultConfig != null ? convertToVO(defaultConfig) : null;
    }

    @Override
    public BotConfigVO getConfigByKey(String configKey) {
        LambdaQueryWrapper<BotConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotConfig::getConfigKey, configKey)
               .isNull(BotConfig::getBotId);
        BotConfig config = this.getOne(wrapper);
        return config != null ? convertToVO(config) : null;
    }

    @Override
    public String getConfigValue(String configKey, String botId) {
        BotConfigVO config = getConfigByKeyAndBotId(configKey, botId);
        return config != null ? config.getConfigValue() : null;
    }

    @Override
    public String getConfigValue(String configKey) {
        return getConfigValue(configKey, (String) null);
    }

    @Override
    public String getConfigValueOrDefault(String configKey, String defaultValue) {
        LambdaQueryWrapper<BotConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotConfig::getConfigKey, configKey)
               .isNull(BotConfig::getBotId);
        BotConfig config = this.getOne(wrapper);
        if (config == null) {
            return defaultValue;
        }
        return config.getConfigValue() != null ? config.getConfigValue() : defaultValue;
    }

    @Override
    public BotConfigVO createConfig(CreateBotConfigRequest request) {
        ConfigType configType = ConfigType.fromCode(request.getConfigType());
        
        if (request.getConfigValue() != null && !configTypeHandler.validate(configType, request.getConfigValue())) {
            log.warn("配置值验证失败: type={}, value={}", configType, request.getConfigValue());
        }
        
        BotConfig config = new BotConfig();
        config.setBotId(request.getBotId());
        config.setConfigKey(request.getConfigKey());
        config.setConfigValue(request.getConfigValue());
        config.setConfigType(configType.getCode());
        config.setMetadata(serializeMetadata(request.getMetadata()));
        config.setDescription(request.getDescription());
        config.setExplain(request.getExplain());
        config.setCategory(request.getCategory() != null ? request.getCategory() : "general");
        config.setEditable(request.getEditable() != null ? (request.getEditable() ? 1 : 0) : 1);
        this.save(config);
        log.info("创建 Bot 配置成功: botId={}, key={}, type={}", config.getBotId(), config.getConfigKey(), configType);
        return convertToVO(config);
    }

    @Override
    public BotConfigVO updateConfig(UpdateBotConfigRequest request) {
        BotConfig config = this.getById(request.getId());
        if (config == null) {
            return null;
        }
        if (request.getConfigValue() != null && !isEditable(config)) {
            log.warn("配置不可编辑，拒绝修改值: id={}, key={}", config.getId(), config.getConfigKey());
            return null;
        }

        String oldValue = config.getConfigValue();

        if (request.getBotId() != null) {
            config.setBotId(request.getBotId());
        }
        if (request.getConfigKey() != null) {
            config.setConfigKey(request.getConfigKey());
        }
        if (request.getConfigValue() != null) {
            ConfigType configType = ConfigType.fromCode(request.getConfigType() != null ? request.getConfigType() : config.getConfigType());
            if (!configTypeHandler.validate(configType, request.getConfigValue())) {
                log.warn("配置值验证失败: type={}, value={}", configType, request.getConfigValue());
            }
            config.setConfigValue(request.getConfigValue());
        }
        if (request.getConfigType() != null) {
            config.setConfigType(request.getConfigType());
        }
        if (request.getMetadata() != null) {
            config.setMetadata(serializeMetadata(request.getMetadata()));
        }
        if (request.getDescription() != null) {
            config.setDescription(request.getDescription());
        }
        if (request.getExplain() != null) {
            config.setExplain(request.getExplain());
        }
        if (request.getCategory() != null) {
            config.setCategory(request.getCategory());
        }
        if (request.getEditable() != null) {
            config.setEditable(request.getEditable() ? 1 : 0);
        }
        config.setUpdatedAt(System.currentTimeMillis());
        this.updateById(config);

        mangoEventPublisher.publish(new BotConfigChangedEvent(
                config.getId(),
                config.getBotId(),
                config.getConfigKey(),
                config.getConfigType(),
                config.getCategory(),
                oldValue,
                config.getConfigValue()
        ));

        log.info("更新 Bot 配置成功: botId={}, key={}", config.getBotId(), config.getConfigKey());
        return convertToVO(config);
    }

    @Override
    public boolean updateConfigValue(String configKey, String botId, String configValue) {
        if (botId == null) {
            return updateConfigValue(configKey, configValue);
        }

        // 不可编辑的默认配置既不复制到 Bot 专属，也不允许修改
        if (!isEditableDefault(configKey)) {
            log.warn("配置不可编辑，拒绝更新: botId={}, key={}", botId, configKey);
            return false;
        }
        
        LambdaQueryWrapper<BotConfig> botWrapper = new LambdaQueryWrapper<>();
        botWrapper.eq(BotConfig::getConfigKey, configKey)
                  .eq(BotConfig::getBotId, botId);
        BotConfig botConfig = this.getOne(botWrapper);
        
        if (botConfig != null) {
            ConfigType configType = ConfigType.fromCode(botConfig.getConfigType());
            if (!configTypeHandler.validate(configType, configValue)) {
                log.warn("配置值验证失败: type={}, value={}", configType, configValue);
            }

            String oldValue = botConfig.getConfigValue();
            botConfig.setConfigValue(configValue);
            botConfig.setUpdatedAt(System.currentTimeMillis());
            boolean result = this.updateById(botConfig);

            if (result) {
                mangoEventPublisher.publish(new BotConfigChangedEvent(
                        botConfig.getId(),
                        botConfig.getBotId(),
                        botConfig.getConfigKey(),
                        botConfig.getConfigType(),
                        botConfig.getCategory(),
                        oldValue,
                        configValue
                ));
                log.info("更新 Bot 配置值成功: botId={}, key={}", botId, configKey);
            }
            return result;
        }
        
        LambdaQueryWrapper<BotConfig> checkBotWrapper = new LambdaQueryWrapper<>();
        checkBotWrapper.eq(BotConfig::getBotId, botId);
        long botConfigCount = this.count(checkBotWrapper);
        
        if (botConfigCount == 0) {
            LambdaQueryWrapper<BotConfig> defaultListWrapper = new LambdaQueryWrapper<>();
            defaultListWrapper.isNull(BotConfig::getBotId);
            List<BotConfig> defaultConfigs = this.list(defaultListWrapper);
            
            for (BotConfig defaultConfig : defaultConfigs) {
                if (!isEditable(defaultConfig)) {
                    continue;
                }
                BotConfig newConfig = new BotConfig();
                newConfig.setBotId(botId);
                newConfig.setConfigKey(defaultConfig.getConfigKey());
                newConfig.setConfigValue(defaultConfig.getConfigValue());
                newConfig.setConfigType(defaultConfig.getConfigType());
                newConfig.setMetadata(defaultConfig.getMetadata());
                newConfig.setDescription(defaultConfig.getDescription());
                newConfig.setExplain(defaultConfig.getExplain());
                newConfig.setCategory(defaultConfig.getCategory());
                newConfig.setEditable(defaultConfig.getEditable());
                this.save(newConfig);
            }
            
            log.info("懒加载复制所有默认配置到 Bot 专属配置: botId={}, count={}", botId, defaultConfigs.size());
            
            LambdaQueryWrapper<BotConfig> newBotWrapper = new LambdaQueryWrapper<>();
            newBotWrapper.eq(BotConfig::getConfigKey, configKey)
                        .eq(BotConfig::getBotId, botId);
            botConfig = this.getOne(newBotWrapper);
        } else {
            LambdaQueryWrapper<BotConfig> defaultWrapper = new LambdaQueryWrapper<>();
            defaultWrapper.eq(BotConfig::getConfigKey, configKey)
                          .isNull(BotConfig::getBotId);
            BotConfig defaultConfig = this.getOne(defaultWrapper);
            
            if (defaultConfig == null) {
                return false;
            }

            ConfigType configType = ConfigType.fromCode(defaultConfig.getConfigType());
            if (!configTypeHandler.validate(configType, configValue)) {
                log.warn("配置值验证失败: type={}, value={}", configType, configValue);
            }

            BotConfig newConfig = new BotConfig();
            newConfig.setBotId(botId);
            newConfig.setConfigKey(configKey);
            newConfig.setConfigValue(configValue);
            newConfig.setConfigType(defaultConfig.getConfigType());
            newConfig.setMetadata(defaultConfig.getMetadata());
            newConfig.setDescription(defaultConfig.getDescription());
            newConfig.setExplain(defaultConfig.getExplain());
            newConfig.setCategory(defaultConfig.getCategory());
            newConfig.setEditable(defaultConfig.getEditable());
            this.save(newConfig);
            
            mangoEventPublisher.publish(new BotConfigChangedEvent(
                    newConfig.getId(),
                    newConfig.getBotId(),
                    newConfig.getConfigKey(),
                    newConfig.getConfigType(),
                    newConfig.getCategory(),
                    defaultConfig.getConfigValue(),
                    configValue
            ));
            
            log.info("懒加载创建单个 Bot 专属配置: botId={}, key={}", botId, configKey);
            return true;
        }
        
        if (botConfig == null) {
            return false;
        }

        ConfigType configType = ConfigType.fromCode(botConfig.getConfigType());
        if (!configTypeHandler.validate(configType, configValue)) {
            log.warn("配置值验证失败: type={}, value={}", configType, configValue);
        }

        String oldValue = botConfig.getConfigValue();
        botConfig.setConfigValue(configValue);
        botConfig.setUpdatedAt(System.currentTimeMillis());
        boolean result = this.updateById(botConfig);

        if (result) {
            mangoEventPublisher.publish(new BotConfigChangedEvent(
                    botConfig.getId(),
                    botConfig.getBotId(),
                    botConfig.getConfigKey(),
                    botConfig.getConfigType(),
                    botConfig.getCategory(),
                    oldValue,
                    configValue
            ));
            log.info("更新 Bot 配置值成功: botId={}, key={}", botId, configKey);
        }
        return result;
    }

    @Override
    public boolean updateConfigValue(String configKey, String configValue) {
        LambdaQueryWrapper<BotConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotConfig::getConfigKey, configKey)
               .isNull(BotConfig::getBotId);
        BotConfig config = this.getOne(wrapper);
        if (config == null) {
            return false;
        }
        if (!isEditable(config)) {
            log.warn("配置不可编辑，拒绝更新: key={}", configKey);
            return false;
        }

        ConfigType configType = ConfigType.fromCode(config.getConfigType());
        if (!configTypeHandler.validate(configType, configValue)) {
            log.warn("配置值验证失败: type={}, value={}", configType, configValue);
        }

        String oldValue = config.getConfigValue();
        config.setConfigValue(configValue);
        config.setUpdatedAt(System.currentTimeMillis());
        boolean result = this.updateById(config);

        if (result) {
            mangoEventPublisher.publish(new BotConfigChangedEvent(
                    config.getId(),
                    config.getBotId(),
                    config.getConfigKey(),
                    config.getConfigType(),
                    config.getCategory(),
                    oldValue,
                    configValue
            ));
            log.info("更新 Bot 配置值成功: key={}", configKey);
        }
        return result;
    }

    @Override
    public boolean deleteConfig(Long id) {
        BotConfig config = this.getById(id);
        if (config == null) {
            return false;
        }
        boolean result = this.removeById(id);
        if (result) {
            log.info("删除 Bot 配置成功: botId={}, key={}", config.getBotId(), config.getConfigKey());
        }
        return result;
    }

    private boolean isEditable(BotConfig config) {
        return config.getEditable() == null || config.getEditable() == 1;
    }

    private boolean isEditableDefault(String configKey) {
        LambdaQueryWrapper<BotConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotConfig::getConfigKey, configKey)
               .isNull(BotConfig::getBotId);
        BotConfig config = this.getOne(wrapper);
        return config == null || isEditable(config);
    }

    private BotConfigVO convertToVO(BotConfig config) {
        BotConfigVO vo = new BotConfigVO();
        vo.setId(config.getId());
        vo.setBotId(config.getBotId());
        vo.setConfigKey(config.getConfigKey());
        vo.setConfigValue(config.getConfigValue());
        vo.setConfigType(config.getConfigType());
        vo.setMetadata(parseMetadata(config.getMetadata()));
        vo.setDescription(config.getDescription());
        vo.setExplain(config.getExplain());
        vo.setCategory(config.getCategory());
        vo.setEditable(config.getEditable() == 1);
        vo.setCreatedAt(config.getCreatedAt());
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }

    private ConfigMetadata parseMetadata(String metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(metadata, ConfigMetadata.class);
        } catch (JsonProcessingException e) {
            log.warn("解析配置元数据失败: {}", metadata, e);
            return null;
        }
    }

    private String serializeMetadata(ConfigMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("序列化配置元数据失败", e);
            return null;
        }
    }
}
