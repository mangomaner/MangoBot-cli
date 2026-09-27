package io.github.mangomaner.mangobot.module.agent.runtime.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.api.MangoModelApi;
import io.github.mangomaner.mangobot.api.enums.ModelRole;
import io.github.mangomaner.mangobot.events.configuration.ModelRoleChangedEvent;
import io.github.mangomaner.mangobot.infra.claude.ClaudeRuntime;
import io.github.mangomaner.mangobot.infra.claude.ClaudeRuntimeProperties;
import io.github.mangomaner.mangobot.infra.claude.process.ClaudeBinaryResolver;
import io.github.mangomaner.mangobot.infra.claude.session.ClaudeSession;
import io.github.mangomaner.mangobot.infra.claude.skill.ClaudeSkillRegistry;
import io.github.mangomaner.mangobot.infra.claude.spec.ClaudeSessionSpec;
import io.github.mangomaner.mangobot.infra.claude.spec.McpServerSpec;
import io.github.mangomaner.mangobot.infra.claude.transcript.ClaudeTranscript;
import io.github.mangomaner.mangobot.module.agent.capability.prompt.SystemPromptComposer;
import io.github.mangomaner.mangobot.module.agent.capability.tool.JavaToolMcpServer;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentMcpConfig;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.service.AgentMcpConfigService;
import io.github.mangomaner.mangobot.module.configuration.model.domain.ModelEndpoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * claude 会话的<b>策略层</b>：决定"这个会话该被配置成什么样"，交给 {@link ClaudeRuntime} 去跑。
 *
 * <p>进程怎么起、轮次怎么排、工作区怎么摆、argv 怎么落，全在 {@code infra/claude}；这里只管往
 * {@link ClaudeSessionSpec} 里填什么——技能目录、系统提示词、工具面、MCP、模型角色端点。
 * 所以本类是唯一需要认识「技能 / 模型设置 / 能力页 / 来源」的地方。
 *
 * <p>端点凭据随 spec 的 {@code env} 进来，spawn 那刻写死，所以换模型要重建会话（见
 * {@link #onModelRoleChanged}）。那个监听只覆盖"角色换绑模型"，改供应商地址/密钥不发事件。
 *
 * <p>业务初始化不用 {@code @PostConstruct}，由 {@code MangoBotApplication.main()} 显式调用 {@link #init()}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@MangoBotEventListener
public class ClaudeRuntimeManager {

    /** 探针会话用的键；它不进常驻会话表，但要和一个真实形状的 spec 一起跑才有意义。 */
    private static final String PROBE_SESSION_KEY = "__probe__";

    /** 聊天会话的池名；常驻上限按池分算，别的 agent 不会来挤这个池的名额。 */
    private static final String CHAT_POOL = "chat";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClaudeRuntime runtime;
    private final ClaudeRuntimeProperties properties;
    private final ClaudeSkillRegistry skillRegistry;
    private final AgentMcpConfigService mcpConfigService;
    private final JavaToolMcpServer javaToolMcpServer;
    private final SystemPromptComposer systemPromptComposer;

    // 本类建过的会话键 → 来源。键怎么拼只有 SessionKeys 知道，业务要"按来源找会话"就该查这张表，
    // 而不是去切那个字符串（切法一变就静默失配）
    private final Map<String, SessionSource> sources = new ConcurrentHashMap<>();

    /** 拉起运行时底座，讲清端点状态，按需探针。由 {@code MangoBotApplication.main()} 显式调用。 */
    public void init() {
        runtime.init();
        if (!runtime.ready()) {
            // 二进制都没有就没必要谈端点；运行时自己的日志已经说清了原因
            return;
        }

        logEndpointState();

        if (properties.isWarmup() && isReady()) {
            runtime.probe(PROBE_SESSION_KEY, () -> specForEphemeral(PROBE_SESSION_KEY,
                    SessionSource.WEB, properties.getBuiltinTools()));
        }
    }

    // ========================================================================
    // 取会话
    // ========================================================================

    /**
     * 取（或创建）某个 Bot + 来源的会话。
     *
     * <p>{@code sourceKey} 是"这个会话在这个 bot 下叫什么"，不是 {@link SessionSource}：
     * 群聊/私聊会带上目标 id（形如 {@code group:12345}），web 就是 sessionId。
     * 会话键因此形如 {@code <botId>:group:12345}。下发哪套技能、连哪个工具端点由 {@code source} 决定。
     */
    public ClaudeSession getOrCreate(String botId, String sourceKey, SessionSource source) {
        if (!isReady()) {
            throw new IllegalStateException("Claude 运行时未就绪：" + notReadyReason());
        }
        String key = botId + ":" + sourceKey;
        sources.put(key, source);
        return runtime.getOrCreate(key, CHAT_POOL, () -> specForChat(key, botId, source));
    }

    // 不带来源的取会话，默认 WEB——给 dev / smoke 端点用
    public ClaudeSession getOrCreate(String botId, String sourceKey) {
        return getOrCreate(botId, sourceKey, SessionSource.WEB);
    }

    /**
     * 造一个<b>不进常驻会话表</b>的一次性会话；用完必须自己 {@link ClaudeSession#close()}。
     *
     * @param source 来源，决定插件目录与连哪个 MCP 端点
     */
    public ClaudeSession newEphemeralSession(String key, SessionSource source) {
        return newEphemeralSession(key, source, properties.getBuiltinTools());
    }

    /** 同 {@link #newEphemeralSession(String, SessionSource)}，但显式指定内置工具白名单（judge 传空串）。 */
    public ClaudeSession newEphemeralSession(String key, SessionSource source, String builtinTools) {
        return runtime.newEphemeral(key, () -> specForEphemeral(key, source, builtinTools));
    }

    /**
     * 造一个 <b>judge（评判）用</b>的一次性会话：无插件、无 MCP、无内置工具。
     *
     * <p>它只读文本、只吐 JSON，多一个工具就多一份把用例原文当指令执行的风险（用例里写的
     * "该沉默"本身就是一段祈使句）。{@code strictMcpConfig} 让它写一份<b>空的</b> {@code mcp.json}
     * 再声明只认它，否则 claude 会去读用户/项目里的 {@code .mcp.json}。
     *
     * @param role         模型角色；没配好时回退主模型
     * @param systemPrompt 评判提示词（<b>整段替换</b> claude 自带的）
     * @throws IllegalStateException 运行时未就绪或评判模型不可用
     */
    public ClaudeSession newJudgeSession(String key, ModelRole role, String systemPrompt) {
        if (!runtime.ready()) {
            throw new IllegalStateException("Claude 运行时未就绪：" + runtime.problem());
        }
        ModelEndpoint endpoint = endpointFor(role);
        if (!endpoint.ok()) {
            throw new IllegalStateException("评判模型不可用：" + endpoint.problem());
        }
        return runtime.newEphemeral(key, () -> specForJudge(endpoint, systemPrompt));
    }

    // ========================================================================
    // 会话生命周期（转发给运行时）
    // ========================================================================

    /**
     * 把单个会话标成"下次拉起时重建"。
     *
     * <p>与 {@link #markAllSessionsStale()} 的区别只在粒度。模拟台用它保证一场模拟从干净进程开始：
     * 固定会话键会命中上一场留下的 warm 进程，不重建就会把上一场的历史与旧提示词一起带进来。
     *
     * <p><b>注意它不丢历史</b>：新进程带 {@code -c} 拉起，会把这场对话接着续下去，只是换上了新的
     * 技能目录与系统提示词。要连历史一起丢，用 {@link #resetSession(String)}。
     */
    public void markSessionStale(String key) {
        runtime.markStale(key);
    }

    /**
     * 把全部常驻会话标成"下次拉起时重建"。技能 / 人格 / Java 工具面变更后调。
     *
     * <p>改完配置<b>只是下次能生效</b>：已经在跑的会话要等下一轮对话才会重建。技能正文还是进程级缓存，
     * 所以变更后业务侧还得给活会话排一条"上文作废、重新读"的控制指令，见 {@code SkillChangeNotifier}。
     */
    public void markAllSessionsStale() {
        runtime.markAllStale();
    }

    /**
     * 打断某个会话正在跑的那一轮。
     *
     * <p>会话不存在（还没拉起过）或没有在途轮次时什么都不做——调用方不需要先判断。
     */
    public void interruptTurn(String key) {
        runtime.interruptTurn(key);
    }

    /**
     * 清空某个会话的上下文：关掉进程，并删掉它自己的工作区目录（转录就在里面）。
     *
     * <p>两件事都要做：<b>只关进程不够</b>——重新拉起时带 {@code -c}，claude 会把磁盘上那份转录
     * 又续回来，用户会看到"点了清空但没生效"。
     *
     * <p>与 {@link #markSessionStale} 的区别：那个是"下次拉起时换新参数"，会话<b>历史保留</b>；
     * 这个是"把历史一起丢掉"。前者用于改配置，后者用于用户点「清空上下文」。
     */
    public boolean resetSession(String key) {
        return runtime.resetSession(key);
    }

    // 预留在跑会话的诊断信息
    public Map<String, Boolean> sessionStatus() {
        return runtime.sessionStatus();
    }

    /**
     * 主模型换绑时重建全部常驻会话：{@code ANTHROPIC_MODEL} 等环境变量在 spawn 那刻就写死了，
     * 不重建进程的话已配置的会话会一直用旧模型答话。
     *
     * <p><b>关进程绝不放在派发线程上做</b>：那个事件池实际只跑到 corePoolSize = 1，全 app 的 OneBot
     * 事件都在这一个线程上派发，而 {@code close()} 要等进程优雅退出（最长 {@code shutdownGraceMillis}），
     * 堵在这里会把消息处理一起拖住。
     */
    @MangoBotEventListener
    public boolean onModelRoleChanged(ModelRoleChangedEvent event) {
        if (!ModelRole.MAIN.getRoleKey().equals(event.getRoleKey())) {
            return true;
        }
        int count = runtime.sessionStatus().size();
        if (count == 0) {
            return true;
        }
        log.info("主模型换绑（{} → {}），重建 {} 个 claude 会话以换用新模型",
                event.getOldModelConfigId(), event.getNewModelConfigId(), count);

        Thread worker = new Thread(() -> log.info("主模型换绑：已关闭 {} 个 claude 会话，下次使用时按新模型重建",
                runtime.closeAll()), "claude-model-changed");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    // ========================================================================
    // 状态查询
    // ========================================================================

    /**
     * 运行时是否可用：二进制那层 + 端点那层都要好。
     *
     * <p>端点每次都现读主模型，所以用户在「模型设置」里补好配置后，这里立刻变成 true。
     */
    public boolean isReady() {
        return runtime.ready() && mainModelEndpoint().ok();
    }

    /** 不可用的原因；就绪时为空串。给启动日志、诊断端点和 smoke 端点复用。 */
    public String notReadyReason() {
        if (!runtime.problem().isEmpty()) {
            return runtime.problem();
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

    /**
     * judge 实际会用的模型端点：优先 {@code preferred}，它没配好就回退主模型。
     *
     * <p>要暴露出来是因为"这一份评判是谁打的"必须可追溯：两者同族时存在自我偏好，
     * 结果里得写清到底用了哪个模型，人才好判断这份评判有多可信。
     */
    public ModelEndpoint judgeEndpoint(ModelRole preferred) {
        return endpointFor(preferred);
    }

    public Path binaryPath() {
        return runtime.binaryPath();
    }

    public Path workingDir() {
        return runtime.workspace().root();
    }

    public String platformKey() {
        return ClaudeBinaryResolver.platformKey();
    }

    // 某个会话自己的 claude 配置目录（转录也在里面）
    public Path sessionConfigDir(String key) {
        return runtime.workspace().configDirOf(key);
    }

    /** 读转录的入口；"查看上下文"页面与用量分析共用它，别再自己去拼路径。 */
    public ClaudeTranscript transcript() {
        return runtime.transcript();
    }

    /** 在跑的会话键 → 它属于哪个来源。给"按来源发控制指令"这类用途。 */
    public Map<String, SessionSource> runningSources() {
        Map<String, Boolean> live = runtime.sessionStatus();
        // 顺手清掉已被回收的，免得这张表随着历史会话无限长
        sources.keySet().retainAll(live.keySet());
        Map<String, SessionSource> out = new LinkedHashMap<>();
        for (String key : live.keySet()) {
            SessionSource source = sources.get(key);
            if (source != null) {
                out.put(key, source);
            }
        }
        return out;
    }

    /**
     * 跑 {@code /context} 取上下文分类明细；失败时返回一句可读的原因。
     *
     * <p>参数与真实会话同源（{@link #specForChat}），少给一个那份报告算的就是另一套配置的占比。
     */
    public String contextReport(String key, String botId, SessionSource source) {
        try {
            return runtime.contextReport(key, () -> specForChat(key, botId, source));
        } catch (IllegalStateException e) {
            // spec 组装时端点就不可用；消息即原因
            return "跑不了：" + e.getMessage() + "。";
        }
    }

    // ========================================================================
    // spec 组装：本类的全部价值都在这里
    // ========================================================================

    /**
     * 聊天会话的 spec：技能按作用域下发、MCP 连本进程的工具 server + 能力页启用的外部连接、
     * 系统提示词是追加的协议头，续会话。
     *
     * <p>技能目录由 {@code SkillManager} 排好（技能在插件目录的 {@code skills/} 子目录里），
     * {@link ClaudeSkillRegistry} 按"会话 → bot → 全局"挑出该用哪份。已跑着的会话不受影响——
     * 这些参数 spawn 那刻就定死了。
     */
    private ClaudeSessionSpec specForChat(String key, String botId, SessionSource source) {
        List<McpServerSpec> servers = new ArrayList<>();
        javaToolServer(key, source).ifPresent(servers::add);
        servers.addAll(configuredServers());

        return new ClaudeSessionSpec(
                splitTools(properties.getBuiltinTools()),
                skillRegistry.resolve(source.getSourceKey(), botId, key),
                servers,
                false,
                null,
                systemPromptComposer.compose(source),
                envOf(mainModelEndpoint()),
                properties.isResumeSessions(),
                Duration.ofSeconds(properties.getTurnTimeoutSeconds()),
                properties.getExtraArgs());
    }

    /** 一次性会话：与聊天同配置，但没有内置工具白名单的调整、且绝不续会话。 */
    private ClaudeSessionSpec specForEphemeral(String key, SessionSource source, String builtinTools) {
        // botId 传空：探针 / judge 没有所属 bot，技能只能回退到全局那一级
        return specForChat(key, null, source)
                .withBuiltinTools(splitTools(builtinTools))
                .asOneShot();
    }

    /**
     * judge 会话的 spec：无插件、无 MCP、无内置工具，且<b>整段替换</b>系统提示词。
     *
     * <p>刻意不共用 {@link #specForChat}：那条路按来源拼了一堆东西，任何一处改动都会悄悄把工具
     * 带进评判会话。"无工具"不该是别的逻辑的副产品。
     */
    private ClaudeSessionSpec specForJudge(ModelEndpoint endpoint, String systemPrompt) {
        return new ClaudeSessionSpec(
                List.of(),
                List.of(),
                List.of(),
                true,
                systemPrompt,
                null,
                envOf(endpoint),
                false,
                Duration.ofSeconds(properties.getTurnTimeoutSeconds()),
                properties.getExtraArgs());
    }

    // 本进程起的 Java 工具 server；一个工具都没暴露时干脆不给，省掉一次注定空手而归的连接
    private Optional<McpServerSpec> javaToolServer(String key, SessionSource source) {
        if (javaToolMcpServer.exposedToolCount(source) == 0) {
            return Optional.empty();
        }
        ObjectNode server = MAPPER.createObjectNode();
        server.put("type", "http");
        server.put("url", javaToolMcpServer.serverUrl(source));
        // 身份必须每次请求都带：一个 MCP server 服务该来源下所有会话，
        // 服务端只能从这里知道是哪个会话在调工具。外部 server 不认识这个头，所以只写在自家这条上。
        server.putObject("headers").put(JavaToolMcpServer.SESSION_HEADER, key);
        return Optional.of(new McpServerSpec(JavaToolMcpServer.MCP_SERVER_NAME, server));
    }

    // 「能力页」上配置并启用的外部 MCP 连接
    private List<McpServerSpec> configuredServers() {
        List<McpServerSpec> specs = new ArrayList<>();
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
            specs.add(new McpServerSpec(name, server));
        }
        return specs;
    }

    // ========================================================================
    // 内部
    // ========================================================================

    // 空格分隔的白名单字符串 → 列表；空串表示一个都不给
    private static List<String> splitTools(String builtinTools) {
        if (builtinTools == null || builtinTools.isBlank()) {
            return List.of();
        }
        return Arrays.stream(builtinTools.trim().split("\\s+")).filter(s -> !s.isBlank()).toList();
    }

    /**
     * 拉起子进程时要注入的端点凭据；只在 spawn 那刻算一次，改完要等会话重建才生效。
     *
     * @throws IllegalStateException 端点不可用；消息即失败原因，会被上层兜底捕获成错误事件
     */
    private static Map<String, String> envOf(ModelEndpoint endpoint) {
        if (!endpoint.ok()) {
            throw new IllegalStateException("主模型不可用：" + endpoint.problem());
        }
        return Map.of(
                "ANTHROPIC_BASE_URL", endpoint.baseUrl(),
                "ANTHROPIC_AUTH_TOKEN", endpoint.apiKey(),
                "ANTHROPIC_MODEL", endpoint.modelName());
    }

    /**
     * 解析某个模型角色的端点，配好就用、没配好就回退主模型。
     *
     * <p>{@code assistant} 角色建了但没指定模型很正常，这时"用主模型评一遍"远比"评不了"有用
     * ——只要结果里写明用的是哪个模型。
     */
    private ModelEndpoint endpointFor(ModelRole role) {
        ModelEndpoint endpoint = MangoModelApi.resolveEndpoint(role);
        if (endpoint.ok() || role == ModelRole.MAIN) {
            return endpoint;
        }
        ModelEndpoint fallback = mainModelEndpoint();
        log.info("模型角色「{}」不可用（{}），回退主模型 {}", role.getRoleKey(), endpoint.problem(),
                fallback.ok() ? fallback.modelName() : "（主模型也不可用）");
        return fallback;
    }

    // 解析 connection_config；不是 JSON 对象就跳过（一条坏配置不该拖垮 spawn）
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

    // claude 的 transport 取值；STDIO 是默认值，不写 type
    private static String transportTypeForClaude(String transportType) {
        if (transportType == null || transportType.isBlank()) {
            return null;
        }
        String normalized = transportType.trim().toLowerCase();
        return "stdio".equals(normalized) ? null : normalized;
    }

    // 启动时把端点状态讲清楚——但只讲地址与模型名，密钥绝不进日志
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
}
