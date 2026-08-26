package io.github.mangomaner.mangobot.module.agent.service;

import io.github.mangomaner.mangobot.module.agent.model.enums.BotType;

/**
 * Bot 类型查询服务：从 bot_configs（config_key=bot.type）读取类型，兜底为模仿人类。
 */
public interface BotTypeService {

    /** 读取 bot 的类型；botId 为 null 或未配置/未知值时返回 HUMAN */
    BotType getBotType(String botId);
}
