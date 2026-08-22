package io.github.mangomaner.mangobot.module.agent.core;

import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.events.configuration.BotConfigChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * agent.compaction 配置变更监听：阈值变更后重建受影响 Agent，使新配置立即生效。
 *
 * <p>通过 {@link ObjectProvider} 懒解析 {@link AgentRuntimeRegistry}，避免本监听器在
 * {@code MangoEventPublisher} 的 @PostConstruct 扫描阶段被提前实例化时拉入注册表，
 * 进而经 BotConfigService 引入对事件发布器自身的循环依赖。
 */
@Component
@RequiredArgsConstructor
@MangoBotEventListener
public class AgentCompactionConfigListener {

    private final ObjectProvider<AgentRuntimeRegistry> registryProvider;

    @MangoBotEventListener
    public boolean onCompactionConfigChanged(BotConfigChangedEvent event) {
        if (!AgentCompactionConfig.CONFIG_KEY.equals(event.getConfigKey())) {
            return true;
        }
        AgentRuntimeRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            return true;
        }
        if (event.getBotId() == null || event.getBotId().isBlank()) {
            registry.reloadAll();
        } else {
            registry.reload(event.getBotId());
        }
        return true;
    }
}
