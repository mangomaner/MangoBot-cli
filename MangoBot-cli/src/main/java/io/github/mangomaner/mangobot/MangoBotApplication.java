package io.github.mangomaner.mangobot;

import io.github.mangomaner.mangobot.adapter.onebot.service.OneBotConfigService;
import io.github.mangomaner.mangobot.api.MangoApiManager;
import io.github.mangomaner.mangobot.api.MangoToolApi;
import io.github.mangomaner.mangobot.module.agent.capability.skill.SkillManager;
import io.github.mangomaner.mangobot.module.agent.capability.skill.SkillSchema;
import io.github.mangomaner.mangobot.module.agent.capability.tool.JavaToolMcpServer;
import io.github.mangomaner.mangobot.module.agent.capability.tool.builtin.DateTimeTool;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
import io.github.mangomaner.mangobot.module.agent.sim.service.SimSchema;
import io.github.mangomaner.mangobot.module.file.service.FileSchema;
import io.github.mangomaner.mangobot.message_handler.response.tools.QqReplyTool;
import io.github.mangomaner.mangobot.plugin.core.PluginManager;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MangoBot 主启动类
 *
 * <p>应用生命周期：
 * 1. Spring Bean 加载阶段：创建所有 Bean，不执行业务初始化
 * 2. CommandLineRunner 阶段：数据库初始化（自动执行）
 * 3. 手动初始化阶段：
 *    [1/5] 静态 API 初始化
 *    [2/5] Skill 同步（能力目录由 SkillManager 一并确保存在）
 *    [3/5] 插件加载
 *    [4/5] Java 工具注册与 MCP server 初始化
 *    [5/5] Claude 运行时初始化 + WebSocket 服务器启动
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
        SkillManager skillManager = context.getBean(SkillManager.class);
        PluginManager pluginManager = context.getBean(PluginManager.class);
        JavaToolMcpServer javaToolMcpServer = context.getBean(JavaToolMcpServer.class);
        ClaudeRuntimeManager claudeRuntimeManager = context.getBean(ClaudeRuntimeManager.class);
        SimSchema simSchema = context.getBean(SimSchema.class);
        FileSchema fileSchema = context.getBean(FileSchema.class);
        SkillSchema skillSchema = context.getBean(SkillSchema.class);

        apiManager.init();
        log.info("[1/5] 静态 API 初始化完成");

        // agent_skill_config 的人格列补列要先于 Skill 同步跑：老库在 DB 文件不存在时才跑的 schema.sql
        // 拿不到新增列，得靠这里 ALTER TABLE；而 SkillManager.init 会读这张表，所以必须放前面。
        skillSchema.init();
        skillManager.init();
        log.info("[2/5] Skill 同步完成（能力目录与按来源的插件目录一并就绪）");

        pluginManager.init();
        log.info("[3/5] 插件加载完成");

        // 兜底建模拟台两张表：本项目没有迁移机制，schema.sql 只在 DB 文件不存在时执行，
        // 老库拿不到这两张表就只能靠这里
        simSchema.init();

        // 同理，bot_files 的表情收藏列也是后加的，老库得靠这里补上
        fileSchema.init();

        // 内置工具先落进 agent_java_tool_config，MCP server 再按「启用的」挂上去。
        // 放在插件加载之后：插件在 init 里经 MangoToolApi 注册的工具也要一起被看见。
        // 回复工具是 bot 的嘴，必须默认启用（registerBuiltinTool），否则新装环境 bot 不会说话。
        MangoToolApi.registerBuiltinTool(QqReplyTool.class,
                List.of(SessionSource.GROUP, SessionSource.PRIVATE));
        MangoToolApi.registerTool(DateTimeTool.class);
        javaToolMcpServer.init();
        log.info("[4/5] Java 工具注册与 MCP server 初始化完成（各来源暴露工具数：{}）",
                Arrays.stream(SessionSource.values())
                        .map(source -> source.getSourceKey() + "="
                                + javaToolMcpServer.exposedToolCount(source))
                        .collect(Collectors.joining(", ")));

        claudeRuntimeManager.init();
        if (claudeRuntimeManager.isReady()) {
            log.info("[5/5] Claude 运行时初始化完成");
        } else {
            log.warn("[5/5] Claude 运行时初始化完成，但不可用：{}", claudeRuntimeManager.notReadyReason());
        }

        OneBotConfigService oneBotConfigService = context.getBean(OneBotConfigService.class);
        oneBotConfigService.startAllEnabledServers();
        log.info("[5/5] 已启用的 WebSocket 服务器启动完成");

        log.info("控制台访问：http://localhost:8082/static/mangobot/");
        log.info("========== MangoBot 启动成功 ==========");
    }
}
