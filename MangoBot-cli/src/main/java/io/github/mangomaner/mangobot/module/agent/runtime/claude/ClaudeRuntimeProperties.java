package io.github.mangomaner.mangobot.module.agent.runtime.claude;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Claude Code 子进程运行时配置。
 *
 * <p><b>注意这里没有端点配置</b>（地址 / 密钥 / 模型名）：那三项统一读「模型设置」里的主模型
 * （{@code model_roles.role_key='main'} → {@code model_configs} → {@code model_providers}），
 * 见 {@code module.configuration.core.ModelProvider#resolveEndpoint}。放在 UI 配置里而不是 YAML，
 * 是因为密钥不该进受版本控制的文件，而且换了模型理应和换其它模型一样在同一个页面生效。
 *
 * <pre>
 * mangobot:
 *   claude:
 *     enabled: true
 *     binary-path:            # 显式指定 claude 可执行文件，留空则按平台自动解析
 *     config-dir:             # CLAUDE_CONFIG_DIR，留空则用 &lt;base&gt;/data/claude-config
 *     working-dir:            # 子进程工作目录，留空则用 &lt;base&gt;/data/claude-work
 *     turn-timeout-seconds: 120
 *     shutdown-grace-millis: 3000
 *     warmup: false           # 应用启动时预拉进程（默认懒启动，避免首次运行卡住开机）
 *     extra-args: []
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "mangobot.claude")
public class ClaudeRuntimeProperties {

    /** 是否启用 Claude Code 运行时 */
    private boolean enabled = true;

    /** 显式指定 claude 可执行文件路径，优先级高于平台自动解析 */
    private String binaryPath;

    /** CLAUDE_CONFIG_DIR 目标目录，留空则用 &lt;base&gt;/data/claude-config */
    private String configDir;

    /** 子进程工作目录，留空则用 &lt;base&gt;/data/claude-work */
    private String workingDir;

    /** 单轮对话超时（秒），超时则杀进程并置错 */
    private long turnTimeoutSeconds = 120;

    /** 关闭时等待进程优雅退出的时长（毫秒），超时后强杀 */
    private long shutdownGraceMillis = 3000;

    /** 应用启动时是否预拉进程；默认懒启动，第一轮对话时才拉起 */
    private boolean warmup = false;

    /** 追加到命令行末尾的额外参数 */
    private List<String> extraArgs = new ArrayList<>();
}
