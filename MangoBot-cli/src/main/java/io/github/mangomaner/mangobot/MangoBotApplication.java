package io.github.mangomaner.mangobot;

import io.github.mangomaner.mangobot.adapter.onebot.service.OneBotConfigService;
import io.github.mangomaner.mangobot.api.MangoApiManager;
import io.github.mangomaner.mangobot.api.MangoToolApi;
import io.github.mangomaner.mangobot.module.agent.capability.skill.SkillManager;
import io.github.mangomaner.mangobot.module.agent.capability.tool.JavaToolMcpServer;
import io.github.mangomaner.mangobot.module.agent.capability.tool.builtin.DateTimeTool;
import io.github.mangomaner.mangobot.module.agent.core.AgentWorkspaceManager;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
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
 *    [1/7] 静态 API 初始化
 *    [2/7] Workspace 初始化（能力目录/旧技能迁移/白名单）
 *    [3/7] Skill 同步
 *    [4/7] 插件加载
 *    [5/7] Java 工具注册与 MCP server 初始化
 *    [6/7] Claude 运行时初始化
 *    [7/7] WebSocket 服务器启动
 * 4. 各 Bot 的 Claude 会话按需懒启动（ClaudeRuntimeManager）
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
        AgentWorkspaceManager agentWorkspaceManager = context.getBean(AgentWorkspaceManager.class);
        SkillManager skillManager = context.getBean(SkillManager.class);
        PluginManager pluginManager = context.getBean(PluginManager.class);
        JavaToolMcpServer javaToolMcpServer = context.getBean(JavaToolMcpServer.class);
        ClaudeRuntimeManager claudeRuntimeManager = context.getBean(ClaudeRuntimeManager.class);

        apiManager.init();
        log.info("[1/7] 静态 API 初始化完成");

        agentWorkspaceManager.init();
        log.info("[2/7] Workspace 初始化完成（能力目录/技能迁移/白名单）");

        skillManager.init();
        log.info("[3/7] Skill 同步完成");

        pluginManager.init();
        log.info("[4/7] 插件加载完成");

        // 内置工具先落进 agent_java_tool_config（幂等，默认禁用），MCP server 再按「启用的」挂上去。
        // 放在插件加载之后：插件在 init 里经 MangoToolApi 注册的工具也要一起被看见。
        MangoToolApi.registerTool(DateTimeTool.class);
        javaToolMcpServer.init();
        log.info("[5/7] Java 工具注册与 MCP server 初始化完成（暴露 {} 个工具）",
                javaToolMcpServer.exposedToolCount());

        claudeRuntimeManager.init();
        if (claudeRuntimeManager.isReady()) {
            log.info("[6/7] Claude 运行时初始化完成");
        } else {
            log.warn("[6/7] Claude 运行时初始化完成，但不可用：{}", claudeRuntimeManager.notReadyReason());
        }

        OneBotConfigService oneBotConfigService = context.getBean(OneBotConfigService.class);
        oneBotConfigService.startAllEnabledServers();
        log.info("[7/7] 已启用的 WebSocket 服务器启动完成");

        log.info("控制台访问：http://localhost:8082/static/mangobot/");
        log.info("========== MangoBot 启动成功 ==========");
    }
}
