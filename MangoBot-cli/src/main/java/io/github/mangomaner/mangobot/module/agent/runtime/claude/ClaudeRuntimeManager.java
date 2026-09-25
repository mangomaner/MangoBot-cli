package io.github.mangomaner.mangobot.module.agent.runtime.claude;

import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.api.MangoModelApi;
import io.github.mangomaner.mangobot.api.enums.ModelRole;
import io.github.mangomaner.mangobot.events.configuration.ModelRoleChangedEvent;
import io.github.mangomaner.mangobot.module.configuration.model.domain.ModelEndpoint;
import io.github.mangomaner.mangobot.utils.FileUtils;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Claude Code 运行时管理器：持有各会话的常驻 claude 进程。
 *
 * <p>会话按 <code>botId:sourceKey</code> 缓存（沿用 {@code AgentRuntimeRegistry} 的命名习惯）。
 * 进程默认懒启动——第一轮对话时才拉起，避免把冷启动和首次运行卡死的风险带进开机流程。
 *
 * <p><b>端点凭据不在这里配置</b>：地址 / 密钥 / 模型名每次拉起子进程时从「模型设置」的主模型
 * 现读（{@link ClaudeRuntimeManager#endpointEnv}）。但环境变量是 spawn 那一刻写死的，所以主模型
 * 换绑时得把常驻会话关掉重建——见 {@link #onModelRoleChanged}。注意那个监听只覆盖"角色换绑模型"，
 * 改供应商的地址/密钥不发事件，那些会话要等到自然重建才会用上新值。
 *
 * <p>遵循项目约定：业务初始化不用 {@code @PostConstruct}，由
 * {@code MangoBotApplication.main()} 显式调用 {@link #init()}；清理走 {@link PreDestroy}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@MangoBotEventListener
public class ClaudeRuntimeManager {

    private static final String DEFAULT_CONFIG_DIR = "data/claude-config";
    private static final String DEFAULT_WORK_DIR = "data/claude-work";
    private static final long PROBE_SETTLE_MILLIS = 2000L;

    private final ClaudeRuntimeProperties properties;

    private final Map<String, ClaudeSession> sessions = new ConcurrentHashMap<>();

    private volatile Path binary;
    private volatile Path configDir;
    private volatile Path workingDir;

    /**
     * 初始化阶段判定的不可用原因（被禁用 / 找不到二进制）；这一层没问题时为空串。
     *
     * <p>端点那一层刻意不缓存在这里：用户可能启动后才去「模型设置」补主模型，
     * 缓存住会让运行时永远卡在未就绪。
     */
    private volatile String runtimeProblem = "";

    /**
     * 初始化：解析路径、播种配置、定位二进制。由 {@code MangoBotApplication.main()} 显式调用。
     *
     * <p>端点没配好**不阻止初始化**，只打 warn：那是可以在 UI 上补救的状态，不是启动失败。
     */
    public void init() {
        if (!properties.isEnabled()) {
            runtimeProblem = "mangobot.claude.enabled=false";
            log.info("Claude 运行时未启用（mangobot.claude.enabled=false）");
            return;
        }

        this.configDir = resolveDir(properties.getConfigDir(), DEFAULT_CONFIG_DIR);
        this.workingDir = resolveDir(properties.getWorkingDir(), DEFAULT_WORK_DIR);
        FileUtils.createDirectory(configDir);
        FileUtils.createDirectory(workingDir);

        ClaudeConfigSeeder.seed(configDir, workingDir);

        Optional<Path> resolved = ClaudeBinaryResolver.resolve(properties);
        if (resolved.isEmpty()) {
            runtimeProblem = "未找到 claude 可执行文件";
            log.error("未找到 claude 可执行文件，运行时不可用。已依次尝试：mangobot.claude.binary-path 配置 → "
                            + "环境变量 {} → {} → {} → PATH 中的 {}。修法二选一：把可执行文件保存为 {}；"
                            + "或已经装过 claude 的话，把 binary-path 指向它现有的文件。",
                    ClaudeBinaryResolver.ENV_OVERRIDE, ClaudeBinaryResolver.distPath(),
                    ClaudeBinaryResolver.userHomePath(), ClaudeBinaryResolver.binaryName(),
                    ClaudeBinaryResolver.distPath());
            return;
        }

        this.binary = resolved.get();
        runtimeProblem = "";
        log.info("Claude 运行时二进制就绪：binary={} platform={} configDir={} workingDir={}",
                binary, ClaudeBinaryResolver.platformKey(), configDir, workingDir);

        logEndpointState();

        if (properties.isWarmup() && isReady()) {
            probe();
        }
    }

    /**
     * 取（或创建）某个 Bot + 来源的会话。
     */
    public ClaudeSession getOrCreate(String botId, String sourceKey) {
        if (!isReady()) {
            throw new IllegalStateException("Claude 运行时未就绪：" + notReadyReason());
        }
        String key = botId + ":" + sourceKey;
        return sessions.computeIfAbsent(key, k -> new ClaudeSession(k, binary,
                sessionWorkingDir(k), configDir, properties, this::endpointEnv));
    }

    public boolean isReady() {
        return binary != null && runtimeProblem.isEmpty() && mainModelEndpoint().ok();
    }

    /**
     * 不可用的原因；就绪时为空串。给启动日志、诊断端点和 smoke 端点复用。
     *
     * <p>每次都现读主模型，所以用户在「模型设置」里补好配置后，这里立刻变成空串。
     */
    public String notReadyReason() {
        if (!runtimeProblem.isEmpty()) {
            return runtimeProblem;
        }
        ModelEndpoint endpoint = mainModelEndpoint();
        return endpoint.ok() ? "" : endpoint.problem();
    }

    /**
     * 当前生效的主模型端点（含失败原因）。诊断端点与状态日志用。
     *
     * <p>每次调用都现查数据库，所以用户在「模型设置」里改完立刻反映在这里。
     */
    public ModelEndpoint mainModelEndpoint() {
        return MangoModelApi.resolveEndpoint(ModelRole.MAIN);
    }

    public Path binaryPath() {
        return binary;
    }

    public Path configDir() {
        return configDir;
    }

    public Path workingDir() {
        return workingDir;
    }

    public String platformKey() {
        return ClaudeBinaryResolver.platformKey();
    }

    /** 预留在跑会话的诊断信息。 */
    public Map<String, Boolean> sessionStatus() {
        Map<String, Boolean> status = new ConcurrentHashMap<>();
        sessions.forEach((key, session) -> status.put(key, session.isRunning()));
        return status;
    }

    /**
     * 主模型换绑时重建全部常驻会话。
     *
     * <p>{@code ANTHROPIC_MODEL} 等环境变量在 spawn 那一刻就写死了，不重建进程的话已配置的会话
     * 会一直用旧模型答话。
     *
     * <p><b>关进程绝不放在派发线程上做</b>：{@code MangoEventPublisher} 那个池虽然写了
     * {@code maximumPoolSize=10}，但配的是无界队列，实际只会跑到 corePoolSize = 1——也就是全 app
     * 的 OneBot 事件（含所有群消息）都在这一个线程上派发。而 {@code close()} 要等进程优雅退出，
     * 最长 {@code shutdownGraceMillis}，堵在这里会把消息处理一起拖住。
     */
    @MangoBotEventListener
    public boolean onModelRoleChanged(ModelRoleChangedEvent event) {
        if (!ModelRole.MAIN.getRoleKey().equals(event.getRoleKey())) {
            return true;
        }

        int count = sessions.size();
        if (count == 0) {
            return true;
        }
        log.info("主模型换绑（{} → {}），重建 {} 个 claude 会话以换用新模型",
                event.getOldModelConfigId(), event.getNewModelConfigId(), count);

        Thread worker = new Thread(() -> log.info("主模型换绑：已关闭 {} 个 claude 会话，下次使用时按新模型重建",
                closeAllSessions()), "claude-model-changed");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down {} claude session(s)", sessions.size());
        closeAllSessions();
    }

    /**
     * 关闭并移除全部常驻会话，返回实际关掉的数量。
     *
     * <p>逐个 {@code remove} 再从 map 里拿，而不是遍历 {@code values()} 后 {@code clear()}：后者在
     * 并发新建会话时会把新条目一起清掉却不关进程，留下孤儿进程。
     */
    private int closeAllSessions() {
        int closed = 0;
        for (String key : List.copyOf(sessions.keySet())) {
            ClaudeSession session = sessions.remove(key);
            if (session == null) {
                continue;
            }
            try {
                session.close();
            } catch (Exception e) {
                log.warn("Failed to close claude session {}", key, e);
            }
            closed++;
        }
        return closed;
    }

    // ========================================================================
    // 内部
    // ========================================================================

    /**
     * 开机探针：拉一个进程起来，等一小会儿再关掉，把"二进制加载不了/缺 DLL/起来就崩"这类
     * 问题在启动时暴露，而不是等到第一条群消息。
     *
     * <p>注意它<b>不是</b>预热：会话是按 {@code botId:sourceKey} 分进程的，探针那个进程
     * 帮不到任何真实会话。也刻意不在这里发一轮真实对话——那要花钱、还依赖主模型配好可用，
     * 不适合放在默认开关后面。要验证"真的能对话"用 {@code /api/claude/smoke}。
     */
    private void probe() {
        ClaudeSession probe = null;
        try {
            probe = new ClaudeSession("__probe__", binary, sessionWorkingDir("__probe__"), configDir,
                    properties, this::endpointEnv);
            probe.warmup();
            Thread.sleep(PROBE_SETTLE_MILLIS);
            boolean running = probe.isRunning();
            if (running) {
                log.info("Claude 运行时探针：进程存活 {}ms 无异常退出", PROBE_SETTLE_MILLIS);
            } else {
                log.error("Claude 运行时探针：进程起来就退了，首轮真实对话必然失败。stderr 尾部：{}",
                        String.join(" | ", probe.stderrTail()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Claude 运行时探针失败——首轮真实对话很可能同样失败", e);
        } finally {
            if (probe != null) {
                probe.close();
            }
        }
    }

    /**
     * 拉起子进程时现读一次主模型，转成要注入的环境变量。
     *
     * <p>实测（claude v2.1.128）这三项齐备即可跑通一轮，不需要 {@code ANTHROPIC_DEFAULT_*_MODEL}
     * 之类的别名，也不需要 {@code --model} 命令行参数。
     *
     * @throws IllegalStateException 主模型不可用；消息即失败原因，会被上层的兜底捕获成错误事件
     */
    private Map<String, String> endpointEnv() {
        ModelEndpoint endpoint = mainModelEndpoint();
        if (!endpoint.ok()) {
            throw new IllegalStateException("主模型不可用：" + endpoint.problem());
        }
        return Map.of(
                "ANTHROPIC_BASE_URL", endpoint.baseUrl(),
                "ANTHROPIC_AUTH_TOKEN", endpoint.apiKey(),
                "ANTHROPIC_MODEL", endpoint.modelName());
    }

    /** 启动时把端点状态讲清楚——但只讲地址与模型名，密钥绝不进日志。 */
    private void logEndpointState() {
        ModelEndpoint endpoint = mainModelEndpoint();
        if (endpoint.ok()) {
            log.info("Claude 运行时端点就绪（读自「模型设置」的主模型）：baseUrl={} model={} provider={}",
                    endpoint.baseUrl(), endpoint.modelName(), endpoint.providerName());
        } else {
            log.warn("Claude 运行时端点不可用：{}。到「模型设置」里把主模型配好即可，"
                    + "不需要重启——下次拉起子进程时会重新读。", endpoint.problem());
        }
    }

    /** 会话工作目录需为文件系统安全名：Windows 路径段不允许 ':'，故把 key 里的分隔符替换掉。 */
    private Path sessionWorkingDir(String key) {
        String safe = key.replaceAll("[^A-Za-z0-9_-]", "_");
        return workingDir.resolve(safe);
    }

    private static Path resolveDir(String configured, String fallback) {
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured.trim()).toAbsolutePath().normalize();
        }
        return FileUtils.resolvePath(fallback);
    }
}
