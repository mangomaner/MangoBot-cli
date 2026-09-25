package io.github.mangomaner.mangobot;

import io.github.mangomaner.mangobot.adapter.onebot.service.OneBotConfigService;
import io.github.mangomaner.mangobot.api.MangoApiManager;
import io.github.mangomaner.mangobot.module.agent.capability.mcp.McpConnectionManager;
import io.github.mangomaner.mangobot.module.agent.capability.skill.SkillManager;
import io.github.mangomaner.mangobot.module.agent.capability.tool.ToolRegistrationService;
import io.github.mangomaner.mangobot.module.agent.core.AgentWorkspaceManager;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
import io.github.mangomaner.mangobot.module.configuration.core.ModelProvider;
import io.github.mangomaner.mangobot.plugin.core.PluginManager;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MangoBot 主启动类
 *
 * <p>应用生命周期：
 * 1. Spring Bean 加载阶段：创建所有 Bean，不执行业务初始化
 * 2. CommandLineRunner 阶段：数据库初始化（自动执行）
 * 3. 手动初始化阶段：
 *    [1/9] 静态 API 初始化
 *    [2/9] 模型初始化
 *    [3/9] 内置工具注册
 *    [4/9] Workspace 初始化（能力目录/旧技能迁移/白名单）
 *    [5/9] Skill 同步
 *    [6/9] MCP 连接初始化
 *    [7/9] 插件加载
 *    [8/9] Claude 运行时初始化
 *    [9/9] WebSocket 服务器启动
 * 4. 各 Bot 的 HarnessAgent 按需懒加载（AgentRuntimeRegistry）
 */
@SpringBootApplication(exclude = {RedisAutoConfiguration.class})
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@MapperScan("io.github.mangomaner.mangobot.system.mapper")
@Slf4j
public class MangoBotApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(MangoBotApplication.class, args);

        log.info("========== MangoBot 初始化开始 ==========");

        MangoApiManager apiManager = context.getBean(MangoApiManager.class);
        ModelProvider modelProvider = context.getBean(ModelProvider.class);
        ToolRegistrationService toolRegistrationService = context.getBean(ToolRegistrationService.class);
        AgentWorkspaceManager agentWorkspaceManager = context.getBean(AgentWorkspaceManager.class);
        SkillManager skillManager = context.getBean(SkillManager.class);
        McpConnectionManager mcpConnectionManager = context.getBean(McpConnectionManager.class);
        PluginManager pluginManager = context.getBean(PluginManager.class);
        ClaudeRuntimeManager claudeRuntimeManager = context.getBean(ClaudeRuntimeManager.class);

        apiManager.init();
        log.info("[1/9] 静态 API 初始化完成");

        modelProvider.init();
        log.info("[2/9] 模型初始化完成");

        toolRegistrationService.initBuiltInTools();
        log.info("[3/9] 内置工具注册完成");

        agentWorkspaceManager.init();
        log.info("[4/9] Workspace 初始化完成（能力目录/技能迁移/白名单）");

        skillManager.init();
        log.info("[5/9] Skill 同步完成");

        mcpConnectionManager.init();
        log.info("[6/9] MCP 连接初始化完成");

        pluginManager.init();
        log.info("[7/9] 插件加载完成");

        claudeRuntimeManager.init();
        if (claudeRuntimeManager.isReady()) {
            log.info("[8/9] Claude 运行时初始化完成");
        } else {
            log.warn("[8/9] Claude 运行时初始化完成，但不可用：{}", claudeRuntimeManager.notReadyReason());
        }

        OneBotConfigService oneBotConfigService = context.getBean(OneBotConfigService.class);
        oneBotConfigService.startAllEnabledServers();
        log.info("[9/9] 已启用的 WebSocket 服务器启动完成");

        log.info("控制台访问：http://localhost:8082/static/mangobot/");
        log.info("========== MangoBot 启动成功 ==========");
    }
}
