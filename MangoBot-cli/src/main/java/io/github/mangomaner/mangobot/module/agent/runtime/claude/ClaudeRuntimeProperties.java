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
 *     max-images-per-turn: 3  # 一轮最多送几张入站图（0 表示只送文字描述）
 *     group-debounce-millis: 1000
 *     session-idle-minutes: 30
 *     max-sessions: 16
 *     max-system-prompt-chars: 8000
 *     builtin-tools: Skill    # claude 内置工具白名单（空格分隔）；空串=全禁
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

    /**
     * 一轮最多带几张入站图。
     *
     * <p>图片落在提示词缓存边界之外，每轮都是净增输入（实测一张 1.5KB 小图约 +250 token，
     * 随尺寸变），所以这是唯一的成本闸门。取消息里最后 N 张，越近越相关。
     */
    private int maxImagesPerTurn = 3;

    /**
     * 群聊/私聊攒消息的防抖窗口（毫秒）：窗口内每来一条就把定时器重置，静默这么久才开一轮。
     *
     * <p>比"首条立刻开跑"更像真人（真人不会 300ms 内回话），也天然把连发的几条并成一批。
     */
    private long groupDebounceMillis = 1000;

    /** 会话空闲多久后回收（分钟）；0 或负数表示不回收 */
    private long sessionIdleMinutes = 30;

    /** 常驻会话数上限，超出按最久未用淘汰；0 或负数表示不限 */
    private int maxSessions = 16;

    /** 系统提示词的字符数上限（0 或负数表示不限）；超长截断并打 warn。 */
    private int maxSystemPromptChars = 8000;

    /**
     * 允许模型使用的 claude <b>内置</b>工具，空格分隔的白名单；空串表示一个都不给。
     *
     * <p>默认只给 {@code Skill}。原因有两条，缺一不可：
     * <ol>
     *   <li>技能正文只能靠 {@code Skill} 工具按需加载（{@code --plugin-dir} 只递目录、不注入正文）。
     *       一旦压掉它，claude 会认为 skill 机制不可用，<b>把技能广告整段从系统提示词里撤掉</b>，
     *       等于所有 SKILL.md 都"点不动"。</li>
     *   <li>其余 20 多个工具 schema（Bash/Read/Write…）对本项目毫无用处——bot 的出口是 MCP 工具，
     *       不是 shell。带上它们白烧输入 token（实测 5390 → 23054 量级）。</li>
     * </ol>
     *
     * <p>注意这与 {@code --allowedTools}（权限名单）是两件事：那个管"放不放行"，这个管"存不存在"。
     * 改这里的值必须重启（spawn 参数是冻的）。
     */
    private String builtinTools = "Skill";

    /** 追加到命令行末尾的额外参数 */
    private List<String> extraArgs = new ArrayList<>();
}
