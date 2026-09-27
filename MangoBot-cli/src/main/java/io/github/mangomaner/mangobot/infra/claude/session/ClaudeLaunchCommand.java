package io.github.mangomaner.mangobot.infra.claude.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mangomaner.mangobot.infra.claude.spec.ClaudeSessionSpec;
import io.github.mangomaner.mangobot.infra.claude.spec.McpServerSpec;
import io.github.mangomaner.mangobot.infra.claude.workspace.ClaudeWorkspace;
import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 把 {@link ClaudeSessionSpec} 落成 claude 的命令行参数——<b>argv 顺序的唯一出处</b>。
 *
 * <p><b>顺序有硬约束，别调</b>：{@code --allowedTools} 与 {@code --plugin-dir} 是变长参数，
 * 必须排在单值选项之前——变长参数靠"后面紧跟一个选项"来终止，插在中间会把后续参数一起吞掉。
 * 所以 {@code --append-system-prompt} 只能排最后。
 *
 * <p>{@code --tools} 不在这里，由 {@code ClaudeProcess} 拼在最前（值同取 {@code builtinTools}）。
 */
@Slf4j
public final class ClaudeLaunchCommand {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ClaudeLaunchCommand() {
    }

    /**
     * 这个会话要用的全部额外参数；顺带把 {@code mcp.json} 写进会话目录——{@code --mcp-config}
     * 指向的那个文件必须与参数同时就位，分开做迟早出现"参数有了文件没有"。
     */
    public static List<String> argsOf(ClaudeSessionSpec spec, ClaudeWorkspace workspace, String key) {
        List<String> args = new ArrayList<>();

        List<String> allowRules = allowRules(spec);
        if (!allowRules.isEmpty()) {
            args.add("--allowedTools");
            args.add(String.join(" ", allowRules));
        }

        for (Path pluginDir : spec.pluginDirs()) {
            args.add("--plugin-dir");
            args.add(pluginDir.toString());
        }

        writeMcpConfig(spec, workspace, key).ifPresent(file -> {
            args.add("--mcp-config");
            args.add(file.toString());
            // 只认我们给的这份：否则 claude 还会去读用户/项目里的 .mcp.json
            args.add("--strict-mcp-config");
        });

        if (spec.systemPrompt() != null && !spec.systemPrompt().isBlank()) {
            args.add("--system-prompt");
            args.add(spec.systemPrompt());
        }
        if (spec.appendSystemPrompt() != null && !spec.appendSystemPrompt().isBlank()) {
            args.add("--append-system-prompt");
            args.add(spec.appendSystemPrompt());
        }
        return args;
    }

    /**
     * 要放行的工具规则 = 每个 MCP server 一条 + <b>授予了存在权的内置工具</b>。
     *
     * <p><b>两扇门必须一起开</b>：{@code --tools} 管"存不存在"、这里管"放不放行"。只开前者的话，
     * headless（{@code -p}）下没人能点「批准」，工具一用就被静默拒掉——症状像"模型不会用"，
     * 只有转录里那行 {@code tool_result} 能看出来。所以名单从 {@code builtinTools} 反推。
     */
    public static List<String> allowRules(ClaudeSessionSpec spec) {
        List<String> rules = new ArrayList<>();
        for (McpServerSpec server : spec.mcpServers()) {
            rules.add(server.allowRule());
        }
        for (String tool : spec.builtinTools()) {
            if (!tool.isBlank() && !rules.contains(tool)) {
                rules.add(tool);
            }
        }
        return rules;
    }

    /**
     * 写成 claude 认的 {@code {"mcpServers":{...}}} 文件。
     *
     * <p>一条连接都没有时：{@code strictMcpConfig} 为真写一份空的再声明只认它；为假返回
     * {@code empty}（不传 {@code --mcp-config}），免得 spawn 参数里多一份没意义的文件。
     */
    private static Optional<Path> writeMcpConfig(ClaudeSessionSpec spec, ClaudeWorkspace workspace, String key) {
        ObjectNode servers = MAPPER.createObjectNode();
        for (McpServerSpec server : spec.mcpServers()) {
            servers.set(server.name(), server.config());
        }
        if (servers.isEmpty() && !spec.strictMcpConfig()) {
            return Optional.empty();
        }

        ObjectNode root = MAPPER.createObjectNode();
        root.set("mcpServers", servers);

        workspace.ensure(key);
        Path file = workspace.mcpFileOf(key);
        FileUtils.writeString(file, root.toPrettyString());
        log.info("已为会话写入 {} 条 MCP 连接配置：{}", servers.size(), file);
        return Optional.of(file);
    }
}
