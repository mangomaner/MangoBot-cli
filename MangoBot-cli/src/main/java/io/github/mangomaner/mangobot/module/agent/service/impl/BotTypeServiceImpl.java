package io.github.mangomaner.mangobot.module.agent.service.impl;

import io.github.mangomaner.mangobot.module.agent.model.enums.BotType;
import io.github.mangomaner.mangobot.module.agent.service.BotTypeService;
import io.github.mangomaner.mangobot.module.configuration.service.BotConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 读取 bot_configs（config_key=bot.type）得到 bot 类型；未配置或值非法时回退为模仿人类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotTypeServiceImpl implements BotTypeService {

    private static final String CONFIG_KEY = "bot.type";

    private final BotConfigService botConfigService;

    @Override
    public BotType getBotType(String botId) {
        if (botId == null) {
            return BotType.defaultType();
        }
        String value = botConfigService.getConfigValue(CONFIG_KEY, botId);
        if (!StringUtils.hasText(value)) {
            return BotType.defaultType();
        }
        // 兼容历史脏数据：前端曾把整卡 JSON 编码（如 "instruction"）落入库，这里剥掉外包围住的双引号
        try {
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            return BotType.fromCode(value);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown bot type config value '{}' for botId={}, fallback to HUMAN", value, botId);
            return BotType.defaultType();
        }
    }
}
