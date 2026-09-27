package io.github.mangomaner.mangobot.infra.claude.spec;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 一次会话「被配置成什么样」——infra 与调用方之间唯一的接缝对象。
 *
 * <p>这里只有数据。调用方决定填什么，infra 负责把受控字段按正确顺序落成命令行参数（见
 * {@code ClaudeLaunchCommand}）并写对 {@code mcp.json}。用具名字段而非裸参数列表，
 * 是因为 argv 顺序有硬约束，而那属于 CLI 的协议知识。
 *
 * @param builtinTools       进 {@code --tools}（管"存不存在"）；空表示一个都不给。放行名单由它反推
 * @param pluginDirs         逐个进 {@code --plugin-dir}；形状必须是 {@code <dir>/skills/<技能名>/SKILL.md}
 * @param mcpServers         写进 {@code mcp.json} 的连接
 * @param strictMcpConfig    列表为空时：真→写空 mcp.json 且加 {@code --strict-mcp-config}（"就是没有工具"）；
 *                           假→完全不传 {@code --mcp-config}。列表非空时总是加
 * @param systemPrompt       非空则下 {@code --system-prompt}（<b>整段替换</b> claude 自带提示词）
 * @param appendSystemPrompt 非空则下 {@code --append-system-prompt}（追加）。聊天会话用这个而不是上面那个：
 *                           bot 要靠模型主动调 {@code send_reply} 才能说话，而 claude 自带提示词里
 *                           那段"什么时候该用工具"的引导正是它的依据
 * @param env                端点凭据；spawn 那刻冻住，改完要重建会话
 * @param resume             带 {@code -c} 续上该工作目录里最近一次对话；一次性会话必须 false
 * @param turnTimeout        单轮超时，超时杀进程
 * @param extraArgs          逃生口；插在受控字段之后
 */
public record ClaudeSessionSpec(
        List<String> builtinTools,
        List<Path> pluginDirs,
        List<McpServerSpec> mcpServers,
        boolean strictMcpConfig,
        String systemPrompt,
        String appendSystemPrompt,
        Map<String, String> env,
        boolean resume,
        Duration turnTimeout,
        List<String> extraArgs) {

    private static final Duration DEFAULT_TURN_TIMEOUT = Duration.ofSeconds(120);

    public ClaudeSessionSpec {
        builtinTools = builtinTools == null ? List.of() : List.copyOf(builtinTools);
        pluginDirs = pluginDirs == null ? List.of() : List.copyOf(pluginDirs);
        mcpServers = mcpServers == null ? List.of() : List.copyOf(mcpServers);
        env = env == null ? Map.of() : Map.copyOf(env);
        extraArgs = extraArgs == null ? List.of() : List.copyOf(extraArgs);
        turnTimeout = turnTimeout == null ? DEFAULT_TURN_TIMEOUT : turnTimeout;
    }

    // 换一份内置工具白名单，其余不变
    public ClaudeSessionSpec withBuiltinTools(List<String> tools) {
        return new ClaudeSessionSpec(tools, pluginDirs, mcpServers, strictMcpConfig,
                systemPrompt, appendSystemPrompt, env, resume, turnTimeout, extraArgs);
    }

    // 关掉续会话——一次性会话每次都必须从干净上下文开始
    public ClaudeSessionSpec asOneShot() {
        return new ClaudeSessionSpec(builtinTools, pluginDirs, mcpServers, strictMcpConfig,
                systemPrompt, appendSystemPrompt, env, false, turnTimeout, extraArgs);
    }
}
