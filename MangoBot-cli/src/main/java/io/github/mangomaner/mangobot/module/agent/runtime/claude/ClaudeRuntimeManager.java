package io.github.mangomaner.mangobot.module.agent.runtime.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.api.MangoModelApi;
import io.github.mangomaner.mangobot.api.enums.ModelRole;
import io.github.mangomaner.mangobot.events.configuration.ModelRoleChangedEvent;
import io.github.mangomaner.mangobot.module.agent.capability.skill.SkillManager;
import io.github.mangomaner.mangobot.module.agent.capability.tool.JavaToolMcpServer;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentMcpConfig;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.service.AgentMcpConfigService;
import io.github.mangomaner.mangobot.module.configuration.model.domain.ModelEndpoint;
import io.github.mangomaner.mangobot.utils.FileUtils;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Claude Code 运行时管理器：持有各会话的常驻 claude 进程。
 *
 * <p>会话按 <code>botId:sourceKey</code> 缓存。
 * 进程默认懒启动——第一轮对话时才拉起，避免把冷启动和首次运行卡死的风险带进开机流程。
 * 常驻会话由巡检线程按空闲时长与数量上限回收，见 {@link #startReaper()}。
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

    /** 空闲回收巡检的间隔（分钟）。 */
    private static final long REAP_INTERVAL_MINUTES = 5L;

    /** 会话工作目录下生成的 MCP 配置文件（--mcp-config 的值） */
    private static final String MCP_CONFIG_FILE = "mcp.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClaudeRuntimeProperties properties;
    private final SkillManager skillManager;
    private final AgentMcpConfigService mcpConfigService;
    private final JavaToolMcpServer javaToolMcpServer;

    private final Map<String, ClaudeSession> sessions = new ConcurrentHashMap<>();

    /** 空闲回收巡检线程；{@code sessionIdleMinutes} 与 {@code maxSessions} 都为 0 时不予创建。 */
    private volatile ScheduledExecutorService reaper;

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
        startReaper();

        if (properties.isWarmup() && isReady()) {
            probe();
        }
    }

    /**
     * 取（或创建）某个 Bot + 来源的会话。
     *
     * <p>{@code sourceKey} 是"这个会话在这个 bot 下叫什么"，不是 {@link SessionSource}：
     * 群聊/私聊会带上目标 id（形如 {@code group:12345}），web 就是 sessionId。
     * 会话键因此形如 {@code <botId>:group:12345}。下发哪套技能由 {@code source} 决定。
     */
    public ClaudeSession getOrCreate(String botId, String sourceKey, SessionSource source) {
        if (!isReady()) {
            throw new IllegalStateException("Claude 运行时未就绪：" + notReadyReason());
        }
        String key = botId + ":" + sourceKey;
        return sessions.computeIfAbsent(key, k -> new ClaudeSession(k, binary,
                sessionWorkingDir(k), configDir, properties, this::endpointEnv,
                () -> capabilityArgs(k, source)));
    }

    /**
     * 不带来源的取会话，默认 {@link SessionSource#WEB}——给 dev / smoke 端点用。
     */
    public ClaudeSession getOrCreate(String botId, String sourceKey) {
        return getOrCreate(botId, sourceKey, SessionSource.WEB);
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
        ScheduledExecutorService timer = reaper;
        if (timer != null) {
            timer.shutdownNow();
        }
        log.info("Shutting down {} claude session(s)", sessions.size());
        closeAllSessions();
    }

    // ========================================================================
    // 空闲回收
    // ========================================================================

    /**
     * 起一个巡检线程：关掉空闲太久的会话、并在超出常驻上限时按最久未用淘汰。
     *
     * <p>没有这条，每个聊过的群都会永久留一个 claude 进程。回收的代价只是那个聊天丢了上下文，
     * 与"新建进程不带历史"是同一个语义，所以可以放心杀——反正下一轮重建的进程同样不带历史。
     */
    private void startReaper() {
        if (properties.getSessionIdleMinutes() <= 0 && properties.getMaxSessions() <= 0) {
            log.info("会话空闲回收未启用（session-idle-minutes 与 max-sessions 都不大于 0）");
            return;
        }
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "claude-session-reaper");
            thread.setDaemon(true);
            return thread;
        });
        timer.scheduleWithFixedDelay(this::reap,
                REAP_INTERVAL_MINUTES, REAP_INTERVAL_MINUTES, TimeUnit.MINUTES);
        reaper = timer;
        log.info("会话空闲回收已启用：每 {} 分钟巡检一次，空闲 {} 分钟回收，常驻上限 {}",
                REAP_INTERVAL_MINUTES, properties.getSessionIdleMinutes(), properties.getMaxSessions());
    }

    /**
     * 一次巡检。跑在 {@code claude-session-reaper} 线程上——<b>关进程绝不能在派发线程做</b>，
     * {@code close()} 最长要等 {@code shutdownGraceMillis} 才强杀。
     */
    private void reap() {
        try {
            closeIdleSessions();
            evictOverflowSessions();
        } catch (Exception e) {
            log.warn("claude 会话巡检失败", e);
        }
    }

    /** 关掉空闲超过 {@code sessionIdleMinutes} 的会话。 */
    private void closeIdleSessions() {
        long idleMinutes = properties.getSessionIdleMinutes();
        if (idleMinutes <= 0) {
            return;
        }
        long thresholdMillis = TimeUnit.MINUTES.toMillis(idleMinutes);
        for (String key : List.copyOf(sessions.keySet())) {
            ClaudeSession session = sessions.get(key);
            if (session == null || session.isTurnInFlight() || session.idleMillis() < thresholdMillis) {
                continue;
            }
            // 先摘出 map 再关：否则并发取会话的人可能拿到这个正在被关掉的实例
            if (sessions.remove(key, session)) {
                log.info("回收空闲 claude 会话：key={} 已空闲 {} 分钟", key, session.idleMillis() / 60_000L);
                closeQuietly(key, session);
            }
        }
    }

    /** 常驻会话数超出 {@code maxSessions} 时，按最久未用（idle 最久）逐个淘汰。 */
    private void evictOverflowSessions() {
        int max = properties.getMaxSessions();
        if (max <= 0) {
            return;
        }
        while (sessions.size() > max) {
            String lruKey = null;
            ClaudeSession lru = null;
            for (Map.Entry<String, ClaudeSession> entry : sessions.entrySet()) {
                ClaudeSession candidate = entry.getValue();
                if (candidate.isTurnInFlight()) {
                    continue;
                }
                if (lru == null || candidate.idleMillis() > lru.idleMillis()) {
                    lru = candidate;
                    lruKey = entry.getKey();
                }
            }
            if (lru == null) {
                // 全都在跑；这一轮不淘汰，下次巡检再看
                return;
            }
            if (!sessions.remove(lruKey, lru)) {
                // 被人抢先移除或换掉了，重新挑
                continue;
            }
            log.info("超出常驻会话上限 {}，淘汰最久未用的：key={} 已空闲 {} 分钟",
                    max, lruKey, lru.idleMillis() / 60_000L);
            closeQuietly(lruKey, lru);
        }
    }

    private void closeQuietly(String key, ClaudeSession session) {
        try {
            session.close();
        } catch (Exception e) {
            log.warn("关闭 claude 会话失败：key={}", key, e);
        }
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
                    properties, this::endpointEnv, () -> capabilityArgs("__probe__", SessionSource.WEB));
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

    /**
     * 能力相关的命令行参数：技能经 {@code --plugin-dir} 下发，MCP 连接经 {@code --mcp-config} 下发。
     *
     * <p>技能是 bot 行为的唯一来源，插件目录由 {@link SkillManager} 维护——那里已按 claude 的插件布局
     * 排好（技能在插件目录下的 {@code skills/} 子目录里），所以这里只负责把路径递下去。
     *
     * <p><b>按来源区分</b>：技能与 Java 工具在「能力页」上都有"启用来源"这一栏，所以这里按
     * {@code source} 取对应的插件目录、连对应的 MCP 端点。已跑着的会话不受影响——这些参数都是
     * spawn 那一刻定死的，改完要等会话重建（空闲回收 / 换主模型 / 重启）才生效。
     *
     * <p>另外还要放行 MCP 工具：本项目的 claude 是 {@code -p} 起的，没有交互界面可点批准，
     * 不写白名单的话 {@code mcp__*} 一律落进 {@code permission_denials}。见 {@link #mcpAllowRules}。
     */
    private List<String> capabilityArgs(String key, SessionSource source) {
        List<String> args = new ArrayList<>();

        // headless（-p）下没有人能点「批准」，不在白名单里的工具一律被拒（结果里是 permission_denials）。
        // 规则写成一条空格分隔的参数：--allowedTools 是变长参数，后面紧跟 --plugin-dir 才会停下；
        // 放在最前面就是为了让它后面一定有选项，不至于把后面的参数一起吞掉。
        List<String> allowRules = mcpAllowRules(source);
        if (!allowRules.isEmpty()) {
            args.add("--allowedTools");
            args.add(String.join(" ", allowRules));
        }

        args.add("--plugin-dir");
        args.add(skillManager.getPluginDirectory(source).toString());

        writeMcpConfig(sessionWorkingDir(key), key, source).ifPresent(file -> {
            args.add("--mcp-config");
            args.add(file.toString());
            // 只认我们给的这份：否则 claude 还会去读用户/项目里的 .mcp.json
            args.add("--strict-mcp-config");
        });
        return args;
    }

    /**
     * 要放行的 MCP 工具规则，形如 {@code mcp__<server名>__*}（claude 认的写法，见其工具名的拼法）。
     *
     * <p>按 server 粒度放行而不是逐个工具名：外部连接的工具有哪些只有连上才知道，这里拿不到清单。
     * 粒度对齐的是「能力页上启用了吗」——启用的连接整条可用，这和以前把工具交给 agent 的语义一致。
     *
     * <p>自己的 Java 工具 server 是按来源分的，所以"有没有工具"要按这个会话的来源问。
     */
    private List<String> mcpAllowRules(SessionSource source) {
        List<String> rules = new ArrayList<>();
        if (javaToolMcpServer.exposedToolCount(source) > 0) {
            rules.add("mcp__" + JavaToolMcpServer.MCP_SERVER_NAME + "__*");
        }
        for (AgentMcpConfig config : mcpConfigService.listEnabled()) {
            String name = config.getMcpName();
            if (name == null || name.isBlank() || JavaToolMcpServer.MCP_SERVER_NAME.equals(name)) {
                continue;
            }
            rules.add("mcp__" + name + "__*");
        }
        return rules;
    }

    /**
     * 写成 claude 认的 {@code {"mcpServers":{...}}} 文件。
     *
     * <p>两条来源合在一起：MangoBot 自己起的 Java 工具 server（本进程内，见 {@link JavaToolMcpServer}），
     * 以及「能力页」上配置并启用的外部 MCP 连接。
     *
     * <p>{@code key} 只写到自家那条 server 的请求头上——外部 server 不认识这个头。身份必须每次请求都带：
     * 一个 MCP server 服务该来源下所有会话，服务端只能从这里知道是哪个会话在调工具。
     *
     * <p>外部连接的 {@code connection_config} 里存的就是单个 server 的对象字面量（页面上是个自由 JSON
     * 文本框），这里只补 claude 需要的 {@code type}：STDIO 不写（默认值），SSE / HTTP 转成小写。
     *
     * <p>两条来源都没东西时返回 empty——此时不传 {@code --mcp-config}，
     * 而不是传一个空的 {@code mcpServers}，免得 spawn 参数里多一份没意义的文件。
     */
    private Optional<Path> writeMcpConfig(Path sessionDir, String key, SessionSource source) {
        ObjectNode servers = MAPPER.createObjectNode();
        int written = writeJavaToolServer(servers, key, source) + writeConfiguredServers(servers);
        if (written == 0) {
            return Optional.empty();
        }

        ObjectNode root = MAPPER.createObjectNode();
        root.set("mcpServers", servers);

        FileUtils.createDirectory(sessionDir);
        Path file = sessionDir.resolve(MCP_CONFIG_FILE);
        FileUtils.writeString(file, root.toPrettyString());
        log.info("已为会话写入 {} 条 MCP 连接配置：{}", written, file);
        return Optional.of(file);
    }

    /**
     * MangoBot 自己起的 Java 工具 server（本进程内），按来源连对应的那个端点。
     *
     * <p>一个工具都没暴露时干脆不写：省掉 claude 侧一次注定空手而归的连接。
     */
    private int writeJavaToolServer(ObjectNode servers, String key, SessionSource source) {
        if (javaToolMcpServer.exposedToolCount(source) == 0) {
            return 0;
        }
        ObjectNode server = servers.putObject(JavaToolMcpServer.MCP_SERVER_NAME);
        server.put("type", "http");
        server.put("url", javaToolMcpServer.serverUrl(source));
        server.putObject("headers").put(JavaToolMcpServer.SESSION_HEADER, key);
        return 1;
    }

    /** 「能力页」上配置并启用的外部 MCP 连接。 */
    private int writeConfiguredServers(ObjectNode servers) {
        int written = 0;
        for (AgentMcpConfig config : mcpConfigService.listEnabled()) {
            String name = config.getMcpName();
            if (name == null || name.isBlank()) {
                log.warn("MCP 连接 id={} 没有名称，跳过", config.getId());
                continue;
            }
            if (JavaToolMcpServer.MCP_SERVER_NAME.equals(name)) {
                log.warn("MCP 连接 id={} 占用了保留名 {}，跳过——这个名字留给 MangoBot 自己的工具 server",
                        config.getId(), name);
                continue;
            }
            ObjectNode server = parseConnectionConfig(config);
            if (server == null) {
                continue;
            }
            String type = transportTypeForClaude(config.getTransportType());
            if (type != null) {
                server.put("type", type);
            }
            servers.set(name, server);
            written++;
        }
        return written;
    }

    /** 解析 {@code connection_config}；不是 JSON 对象就跳过（一条坏配置不该拖垮 spawn）。 */
    private ObjectNode parseConnectionConfig(AgentMcpConfig config) {
        String raw = config.getConnectionConfig();
        if (raw == null || raw.isBlank()) {
            log.warn("MCP 连接「{}」的连接配置为空，跳过", config.getMcpName());
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(raw);
            if (!node.isObject()) {
                log.warn("MCP 连接「{}」的连接配置不是 JSON 对象，跳过", config.getMcpName());
                return null;
            }
            return (ObjectNode) node;
        } catch (Exception e) {
            log.warn("MCP 连接「{}」的连接配置不是合法 JSON，跳过：{}", config.getMcpName(), e.getMessage());
            return null;
        }
    }

    /** claude 的 transport 取值；STDIO 是默认值，不写 type。 */
    private static String transportTypeForClaude(String transportType) {
        if (transportType == null || transportType.isBlank()) {
            return null;
        }
        String normalized = transportType.trim().toLowerCase();
        return "stdio".equals(normalized) ? null : normalized;
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
