package io.github.mangomaner.mangobot.module.agent.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.skill.SkillFilter;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.github.mangomaner.mangobot.api.MangoModelApi;
import io.github.mangomaner.mangobot.api.enums.ModelRole;
import io.github.mangomaner.mangobot.module.agent.capability.mcp.McpConnectionManager;
import io.github.mangomaner.mangobot.module.agent.capability.tool.JavaToolLoader;
import io.github.mangomaner.mangobot.module.agent.middleware.AgentContextCapture;
import io.github.mangomaner.mangobot.module.agent.middleware.MessageSanitizeMiddleware;
import io.github.mangomaner.mangobot.module.agent.middleware.PromptInjectionMiddleware;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentJavaToolConfig;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentMcpConfig;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentMcpToolConfig;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentSkillConfig;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.service.AgentJavaToolConfigService;
import io.github.mangomaner.mangobot.module.agent.service.AgentMcpConfigService;
import io.github.mangomaner.mangobot.module.agent.service.AgentMcpToolConfigService;
import io.github.mangomaner.mangobot.module.agent.service.AgentSkillConfigService;
import io.github.mangomaner.mangobot.system.common.ErrorCode;
import io.github.mangomaner.mangobot.system.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 运行时注册表：每 (Bot, 会话来源) 一个常驻 HarnessAgent（web/group/private 最多各一个）。
 *
 * <p>能力定义全局共用（DB 为 source of truth），但**按来源过滤注册**：
 * 工具/MCP 工具/Skill 依据各自 DB 中的 enabledList/availableList（来源列表）决定该来源的
 * agent 是否加载——因此 web 端的 agent 不会加载群聊/私聊发送工具，也不会出现来源误用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRuntimeRegistry {

    private static final String KEY_SEPARATOR = ":";

    private final AgentWorkspaceManager workspaceManager;
    private final AgentRuntimeProperties properties;
    private final AgentJavaToolConfigService javaToolConfigService;
    private final AgentMcpConfigService mcpConfigService;
    private final AgentMcpToolConfigService mcpToolConfigService;
    private final AgentSkillConfigService skillConfigService;
    private final JavaToolLoader javaToolLoader;
    private final McpConnectionManager mcpConnectionManager;
    private final AgentContextCapture agentContextCapture;
    private final PromptInjectionMiddleware promptInjectionMiddleware;
    private final MessageSanitizeMiddleware messageSanitizeMiddleware;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** key = botId:sourceKey -> HarnessAgent */
    private final Map<String, HarnessAgent> agents = new ConcurrentHashMap<>();

    /** 获取（懒加载）指定 Bot + 来源的常驻 Agent */
    public HarnessAgent getOrCreate(String botId, SessionSource source) {
        final String resolvedBotId = (botId == null || botId.isBlank()) ? "default" : botId;
        SessionSource safeSource = source != null ? source : SessionSource.WEB;
        return agents.computeIfAbsent(key(resolvedBotId, safeSource), k -> buildAgent(resolvedBotId, safeSource));
    }

    /** 兼容入口：默认 WEB 来源 */
    public HarnessAgent getOrCreate(String botId) {
        return getOrCreate(botId, SessionSource.WEB);
    }

    /** 重建指定 Bot 的全部来源 Agent（能力定义/白名单变更后调用） */
    public void reload(String botId) {
        String prefix = (botId == null || botId.isBlank() ? "default" : botId) + KEY_SEPARATOR;
        for (String key : List.copyOf(agents.keySet())) {
            if (key.startsWith(prefix)) {
                HarnessAgent old = agents.remove(key);
                if (old != null) {
                    try {
                        old.close();
                    } catch (Exception e) {
                        log.warn("Failed to close agent for key: {}", key, e);
                    }
                }
            }
        }
    }

    /** 重建所有 Agent（全局能力变更后调用） */
    public void reloadAll() {
        for (String key : List.copyOf(agents.keySet())) {
            HarnessAgent old = agents.remove(key);
            if (old != null) {
                try {
                    old.close();
                } catch (Exception e) {
                    log.error("Failed to close agent for key: {}", key, e);
                }
            }
        }
    }

    /** 关闭所有 Agent（随 Spring 容器关闭） */
    @PreDestroy
    public void shutdownAll() {
        reloadAll();
    }

    private HarnessAgent buildAgent(String botId, SessionSource source) {
        OpenAIChatModel model = MangoModelApi.getModel(ModelRole.MAIN);
        if (model == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "主模型未配置，请先在模型设置中为 main 角色配置模型");
        }

        Toolkit toolkit = new Toolkit();
        registerJavaTools(toolkit, source);
        registerMcpTools(toolkit, source);

        String agentName = "mangobot-" + safeName(botId) + "-" + source.getSourceKey();
        Path workspace = workspaceManager.ensureAgentWorkspace(botId, source);
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(agentName)
                .model(model)
                .workspace(workspace)
                // 关键：project 指向 workspace 本身，避免 overlay 读取 AGENTS.md 时
                // 因命名空间化找不到 workspace 文件而兜底注入 project 根目录的 AGENTS.md（项目开发文档）
                .filesystem(new LocalFilesystemSpec().project(workspace))
                .toolkit(toolkit)
                .enableMetaTool(true)
                .stateStore(new JsonFileAgentStateStore(workspaceManager.getStateDir()))
                .compaction(CompactionConfig.builder()
                        .triggerTokens(properties.getCompaction().getTriggerTokens())
                        .keepTokens(properties.getCompaction().getKeepTokens())
                        .flushBeforeCompact(properties.getCompaction().isFlushBeforeCompact())
                        .build())
                // 关闭 harness 内联同步抽取：长期记忆抽取改由 MemoryFlushService 在后台异步触发，
                // 否则每次回复完成后会阻塞 [DONE]，前端需等待一次 LLM 抽取（可达十余秒）才能再次发送
                .memory(MemoryConfig.builder()
                        .flushTrigger(MemoryConfig.FlushTrigger.never())
                        .build())
                .skillRepository(new FileSystemSkillRepository(workspaceManager.getSkillsDirectory()))
                .skillFilter(buildSkillFilter(source))
                // 保留内置 WorkspaceContextMiddleware：注入 MEMORY.md / knowledge / 工作区清单。
                // AGENTS.md 已退役（AgentWorkspaceManager 迁移时删除，不再播种），不会注入旧人格，
                // 人格与回复规则仍由 PromptInjectionMiddleware 追加的三层提示词承载
                .middleware(messageSanitizeMiddleware)
                .middleware(promptInjectionMiddleware)
                .middleware(agentContextCapture)
                // 系统不使用子代理，关闭以移除 ## Subagents 提示段落与 agent_spawn/send 等工具
                .disableSubagents()
                // 中断发生在“模型已产出 tool_calls 但工具尚未执行”之间时，会在持久化 state 里
                // 留下悬空的 pending tool_use，DeepSeek 会因“assistant tool_calls 后缺 tool 消息”报 400。
                // 开启后 harness 在下次调用时自动为这些悬空工具调用补上错误结果（发生在 compaction 之前）
                .enablePendingToolRecovery(true)
                // 关闭 shell 执行工具（execute）：聊天机器人不应具备任意命令执行能力
                .disableShellTool();

        HarnessAgent agent = builder.build();
        log.info("HarnessAgent built for bot {} source {}: workspace={}",
                botId, source.getSourceKey(), workspaceManager.resolveAgentWorkspace(botId, source));
        return agent;
    }

    /**
     * Java 工具按来源过滤注册：只加载 enabled 且 enabledList 包含该来源的工具
     */
    private void registerJavaTools(Toolkit toolkit, SessionSource source) {
        List<AgentJavaToolConfig> enabledTools = javaToolConfigService.listEnabled();
        for (AgentJavaToolConfig config : enabledTools) {
            if (!isSourceEnabled(config.getEnabledList(), source)) {
                log.debug("Java tool {} not enabled for source {}", config.getClassName(), source.getSourceKey());
                continue;
            }
            try {
                javaToolLoader.loadTool(config).ifPresent(tool -> {
                    toolkit.registerTool(tool);
                    log.debug("Java tool registered: {} ({}) for source {}",
                            config.getClassName(), config.getToolName(), source.getSourceKey());
                });
            } catch (Exception e) {
                log.error("Failed to register Java tool: {}", config.getClassName(), e);
            }
        }
    }

    /**
     * MCP 工具按来源过滤注册
     */
    private void registerMcpTools(Toolkit toolkit, SessionSource source) {
        List<AgentMcpConfig> enabledMcps = mcpConfigService.listEnabled();
        for (AgentMcpConfig mcpConfig : enabledMcps) {
            McpClientWrapper client = mcpConnectionManager.getClient(mcpConfig.getId());
            if (client == null) {
                log.warn("MCP client not connected, skip: {} (ID: {})", mcpConfig.getMcpName(), mcpConfig.getId());
                continue;
            }
            List<String> enabledToolNames = mcpToolConfigService.listByMcpConfigId(mcpConfig.getId()).stream()
                    .filter(AgentMcpToolConfig::getEnabled)
                    .filter(config -> isSourceEnabled(config.getEnabledList(), source))
                    .map(AgentMcpToolConfig::getToolName)
                    .toList();
            if (enabledToolNames.isEmpty()) {
                continue;
            }
            try {
                toolkit.registration()
                        .mcpClient(client)
                        .enableTools(enabledToolNames)
                        .apply();
                log.info("MCP tools registered: {} ({} tools) for source {}",
                        mcpConfig.getMcpName(), enabledToolNames.size(), source.getSourceKey());
            } catch (Exception e) {
                log.error("Failed to register MCP tools: {}", mcpConfig.getMcpName(), e);
            }
        }
    }

    /**
     * Skill 可见性过滤：只暴露 enabled 且 enabledList 包含该来源的技能
     */
    private SkillFilter buildSkillFilter(SessionSource source) {
        List<String> enabledSkillNames = skillConfigService.listEnabled().stream()
                .filter(config -> isSourceEnabled(config.getEnabledList(), source))
                .map(AgentSkillConfig::getSkillName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
        if (enabledSkillNames.isEmpty()) {
            return SkillFilter.none();
        }
        return SkillFilter.only(enabledSkillNames.toArray(String[]::new));
    }

    /**
     * 判断 enabledList（JSON 数组，如 ["web","group"]）是否包含指定来源
     */
    private boolean isSourceEnabled(String enabledListJson, SessionSource source) {
        if (enabledListJson == null || enabledListJson.isBlank()) {
            // 未配置来源列表视为全来源可用（兼容历史数据）
            return true;
        }
        try {
            List<String> enabledSources = objectMapper.readValue(enabledListJson, new TypeReference<List<String>>() {});
            return enabledSources.stream()
                    .anyMatch(item -> item != null && item.equalsIgnoreCase(source.getSourceKey()));
        } catch (Exception e) {
            log.warn("Failed to parse enabledList: {}", enabledListJson, e);
            return true;
        }
    }

    private static String key(String botId, SessionSource source) {
        return botId + KEY_SEPARATOR + source.getSourceKey();
    }

    private static String safeName(String botId) {
        return botId.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
