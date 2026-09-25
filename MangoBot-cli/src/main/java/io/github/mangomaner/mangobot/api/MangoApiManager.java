package io.github.mangomaner.mangobot.api;

import io.github.mangomaner.mangobot.module.agent.capability.tool.ToolRegistrationService;
import io.github.mangomaner.mangobot.module.configuration.core.ModelProvider;
import io.github.mangomaner.mangobot.module.configuration.service.BotConfigService;
import io.github.mangomaner.mangobot.module.configuration.service.PluginConfigService;
import io.github.mangomaner.mangobot.module.configuration.service.SystemConfigService;
import io.github.mangomaner.mangobot.module.file.service.BotFilesService;
import io.github.mangomaner.mangobot.module.message.groupMessage.service.GroupMessagesService;
import io.github.mangomaner.mangobot.adapter.onebot.handler.outbound.send.OneBotApiService;
import io.github.mangomaner.mangobot.module.message.privateMessage.service.PrivateMessagesService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 负责初始化静态 API 类的管理器
 */
@Component
public class MangoApiManager {

    @Resource
    private GroupMessagesService groupMessagesService;

    @Resource
    private PrivateMessagesService privateMessagesService;

    @Resource
    private BotFilesService botFilesService;

    @Resource
    private OneBotApiService oneBotApiService;

    @Resource
    private ModelProvider modelProvider;

    @Resource
    private ToolRegistrationService toolRegistrationService;

    @Resource
    private SystemConfigService systemConfigService;

    @Resource
    private BotConfigService botConfigService;

    @Resource
    private PluginConfigService pluginConfigService;

    /**
     * 初始化静态 API 类
     */
    public void init() {
        MangoGroupMessageApi.setService(groupMessagesService);
        MangoPrivateMessageApi.setService(privateMessagesService);
        MangoFileApi.setService(botFilesService);

        MangoOneBotApi.setService(oneBotApiService);

        MangoModelApi.setProvider(modelProvider);
        MangoToolApi.setService(toolRegistrationService);

        MangoConfigApi.setSystemConfigService(systemConfigService);
        MangoConfigApi.setBotConfigService(botConfigService);
        MangoConfigApi.setPluginConfigService(pluginConfigService);
    }
}
