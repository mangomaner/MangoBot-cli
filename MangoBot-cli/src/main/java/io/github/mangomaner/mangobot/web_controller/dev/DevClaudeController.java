package io.github.mangomaner.mangobot.web_controller.dev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeEvent;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeProperties;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeSession;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.configuration.model.domain.ModelEndpoint;
import io.github.mangomaner.mangobot.system.common.BaseResponse;
import io.github.mangomaner.mangobot.system.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 临时开发测试接口（Claude 运行时）。<b>整套前端「开发测试」页面连同本类是临时设施，可整体删除。</b>
 *
 * <p>与 {@code ClaudeSmokeController} 的分工：那个端点面向人眼与正则，吐的是带 {@code <ClaudeSmoke>}
 * 属性页脚的文本流；本类面向程序消费，吐的是结构化 JSON。两者并存，互不替代。
 *
 * <p>三个端点：
 * <ul>
 *   <li>{@code GET /api/dev/claude/status} —— 运行时状态（二进制解析结果、路径、常驻会话）；</li>
 *   <li>{@code GET /api/dev/claude/config-seed} —— 配置播种检查（{@code <configDir>/.claude.json} 的实况）；</li>
 *   <li>{@code GET /api/dev/claude/stream} —— 一轮对话的原始事件流，每个 SSE 元素是一个单行 JSON。</li>
 * </ul>
 *
 * <p>本类刻意不新增任何正式类的公开方法：状态从 {@link ClaudeRuntimeManager} 已有 getter 与
 * {@link ClaudeRuntimeProperties} 直接读。注意 {@code configDir()} / {@code workingDir()} 在运行时
 * 未初始化时是 {@code null}，三个端点全部判空，不抛异常。
 */
@RestController
@RequestMapping("/api/dev/claude")
@Slf4j
public class DevClaudeController {

    /** dev 流式端点用的会话键（{@code getOrCreate("dev", "default", WEB)}）。 */
    private static final String DEV_SESSION_KEY = "dev:default";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CONFIG_FILE = ".claude.json";
    private static final String DONE = "[DONE]";

    private final ClaudeRuntimeManager runtimeManager;
    private final ClaudeRuntimeProperties properties;

    public DevClaudeController(ClaudeRuntimeManager runtimeManager, ClaudeRuntimeProperties properties) {
        this.runtimeManager = runtimeManager;
        this.properties = properties;
    }

    // ========================================================================
    // 运行时状态
    // ========================================================================

    /** 运行时状态：二进制是否解析到、各路径是否存在、已有哪些常驻会话。 */
    @GetMapping("/status")
    public BaseResponse<StatusVO> status() {
        Path binary = runtimeManager.binaryPath();
        // 配置目录现在是"每个会话一份"（见 ClaudeRuntimeManager.CLAUDE_CONFIG_SUBDIR），
        // 这里报 dev 会话那一份作为实况；要别的会话用 /config-seed?sessionKey=…
        Path configDir = runtimeManager.sessionConfigDir(DEV_SESSION_KEY);
        Path workingDir = runtimeManager.workingDir();
        ModelEndpoint endpoint = runtimeManager.mainModelEndpoint();

        StatusVO vo = new StatusVO(
                properties.isEnabled(),
                runtimeManager.isReady(),
                runtimeManager.notReadyReason(),
                runtimeManager.platformKey(),
                pathText(binary),
                isFile(binary),
                sizeOf(binary),
                pathText(configDir),
                isDir(configDir),
                pathText(workingDir),
                isDir(workingDir),
                properties.getBinaryPath(),
                endpoint.ok() ? endpoint.baseUrl() : null,
                endpoint.ok() ? endpoint.modelName() : null,
                endpoint.ok() ? endpoint.providerName() : null,
                endpoint.ok(),
                properties.getTurnTimeoutSeconds(),
                properties.getShutdownGraceMillis(),
                properties.isWarmup(),
                properties.getExtraArgs(),
                runtimeManager.sessionStatus());
        return ResultUtils.success(vo);
    }

    // ========================================================================
    // 配置播种检查
    // ========================================================================

    /**
     * 配置播种检查：某个会话的 {@code <会话目录>/claude-config/.claude.json} 的实况与关键字段。
     *
     * @param sessionKey 会话键，形如 {@code 3970521445:group:420418118}；默认查 dev 会话
     */
    @GetMapping("/config-seed")
    public BaseResponse<ConfigSeedVO> configSeed(
            @RequestParam(defaultValue = DEV_SESSION_KEY) String sessionKey) {
        Path configDir = runtimeManager.sessionConfigDir(sessionKey);
        if (configDir == null) {
            return ResultUtils.success(ConfigSeedVO.unavailable(
                    "运行时未初始化（mangobot.claude.enabled=false，或未找到 claude 可执行文件），配置目录尚未确定"));
        }

        Path file = configDir.resolve(CONFIG_FILE);
        if (!Files.isRegularFile(file)) {
            return ResultUtils.success(new ConfigSeedVO(pathText(configDir), pathText(file), false,
                    0L, null, null, null, Map.of(), null,
                    "文件不存在。运行时初始化时 ClaudeConfigSeeder 会播种它，检查启动日志"));
        }

        String raw;
        try {
            raw = Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to read claude config {}", file, e);
            return ResultUtils.success(new ConfigSeedVO(pathText(configDir), pathText(file), true,
                    sizeOf(file), null, null, null, Map.of(), null, "读取失败：" + e.getMessage()));
        }

        Boolean onboarding = null;
        String theme = null;
        Map<String, ProjectTrust> projects = new LinkedHashMap<>();
        String pretty = raw;
        String note = null;
        try {
            JsonNode root = MAPPER.readTree(raw);
            if (root != null && root.isObject()) {
                if (root.hasNonNull("hasCompletedOnboarding")) {
                    onboarding = root.get("hasCompletedOnboarding").asBoolean();
                }
                if (root.hasNonNull("theme")) {
                    theme = root.get("theme").asText();
                }
                JsonNode projectsNode = root.path("projects");
                if (projectsNode.isObject()) {
                    projectsNode.fields().forEachRemaining(entry -> {
                        JsonNode v = entry.getValue();
                        projects.put(entry.getKey(), new ProjectTrust(
                                boolOrNull(v, "hasTrustDialogAccepted"),
                                boolOrNull(v, "hasCompletedProjectOnboarding")));
                    });
                }
                pretty = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            } else {
                note = "文件内容不是 JSON 对象，播种会将其重建";
            }
        } catch (Exception e) {
            note = "JSON 解析失败：" + e.getMessage();
        }

        return ResultUtils.success(new ConfigSeedVO(pathText(configDir), pathText(file), true,
                sizeOf(file), lastModified(file), onboarding, theme, projects, pretty, note));
    }

    // ========================================================================
    // 事件流
    // ========================================================================

    /**
     * 跑一轮对话并把原始事件以单行 JSON 逐条流出来。
     *
     * <p>每个 SSE 元素形如：
     * <pre>
     * {"seq":0,"dtMs":123,"elapsedMs":123,"type":"assistant","subtype":"","delta":"你",
     *  "text":"","thinking":"","costUsd":"","durationMs":0,"raw":{…原始事件…}}
     * </pre>
     * {@code dtMs} 递增即证明是流式而非最后一次性吐出；{@code raw} 是 claude 那一行 stream-json 的原样
     * 嵌入，JSON 会自行转义换行，所以整个元素保证单行，SSE 分帧安全。末元素固定为 {@code [DONE]}。
     *
     * <p>会话 key 默认 {@code dev:default}，与真实 Bot 的会话（{@code botId:sourceKey}）完全隔离，
     * 手工测试不会污染线上会话。
     *
     * <p>{@code source} 决定下发哪个来源的技能与工具端点，手工验"群聊技能有没有下发"时就靠它。
     * 它不进会话键——同一个 key 换个 source 会命中已经跑着的那个进程，看到的还是旧参数。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(
            @RequestParam String message,
            @RequestParam(defaultValue = "dev") String botId,
            @RequestParam(defaultValue = "default") String sourceKey,
            @RequestParam(defaultValue = "web") String source) {

        SessionSource sessionSource;
        try {
            sessionSource = SessionSource.fromKey(source);
        } catch (IllegalArgumentException e) {
            return Flux.just(errorLine("bad-source: " + source), DONE);
        }

        if (!runtimeManager.isReady()) {
            return Flux.just(errorLine("runtime-not-ready: " + runtimeManager.notReadyReason()), DONE);
        }

        ClaudeSession session;
        try {
            session = runtimeManager.getOrCreate(botId, sourceKey, sessionSource);
        } catch (Exception e) {
            log.error("Failed to obtain claude session for dev stream", e);
            return Flux.just(errorLine("session-failed: " + e.getMessage()), DONE);
        }

        long startMillis = System.currentTimeMillis();
        AtomicLong lastMillis = new AtomicLong(startMillis);
        AtomicLong seq = new AtomicLong();

        Flux<String> eventLines;
        try {
            // sendTurn 会同步抛（进程要重建但主模型已不可用），那发生在 Flux 建好之前，
            // 兜不住的话 SSE 端点会直接 500，前端 reader 拿不到 [DONE] 就悬挂
            eventLines = session.sendTurn(message)
                    .map(event -> {
                        long now = System.currentTimeMillis();
                        long dt = now - lastMillis.getAndSet(now);
                        return describe(event, seq.getAndIncrement(), dt, now - startMillis);
                    })
                    .onErrorResume(e -> {
                        log.error("Dev stream turn failed", e);
                        return Flux.just(errorLine("turn-failed: " + e.getMessage()));
                    });
        } catch (Exception e) {
            log.error("Dev stream could not start a turn", e);
            return Flux.just(errorLine("turn-failed: " + e.getMessage()), DONE);
        }

        return Flux.concat(eventLines, Flux.just(DONE));
    }

    private static String describe(ClaudeEvent event, long seq, long dtMs, long elapsedMs) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("seq", seq);
        node.put("dtMs", dtMs);
        node.put("elapsedMs", elapsedMs);
        node.put("type", event.type());
        node.put("subtype", event.subtype());
        node.put("delta", event.deltaText());
        node.put("deltaThinking", event.deltaThinking());
        node.put("text", event.assistantText());
        node.put("thinking", event.assistantThinking());
        node.put("costUsd", event.costUsd());
        node.put("durationMs", event.durationMs());
        node.set("raw", event.payload());
        return node.toString();
    }

    private static String errorLine(String message) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("seq", 0);
        node.put("dtMs", 0L);
        node.put("elapsedMs", 0L);
        node.put("type", "dev-error");
        node.put("subtype", "");
        node.put("delta", "");
        node.put("deltaThinking", "");
        node.put("text", message);
        node.put("thinking", "");
        node.put("costUsd", "");
        node.put("durationMs", 0L);
        node.set("raw", MAPPER.createObjectNode().put("error", message));
        return node.toString();
    }

    // ========================================================================
    // 小工具
    // ========================================================================

    private static String pathText(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize().toString();
    }

    private static boolean isFile(Path path) {
        return path != null && Files.isRegularFile(path);
    }

    private static boolean isDir(Path path) {
        return path != null && Files.isDirectory(path);
    }

    private static long sizeOf(Path path) {
        if (!isFile(path)) {
            return 0L;
        }
        try {
            return Files.size(path);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant().toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static Boolean boolOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asBoolean() : null;
    }

    // ========================================================================
    // 返回体
    // ========================================================================

    /**
     * 运行时状态。
     *
     * @param notReadyReason       未就绪的具体原因；就绪时为空串
     * @param binaryPath           实际解析到的可执行文件绝对路径，未就绪时为 {@code null}
     * @param configDir            配置目录绝对路径，运行时未初始化时为 {@code null}
     * @param workingDir           子进程工作目录绝对路径，运行时未初始化时为 {@code null}
     * @param configuredBinaryPath YAML 里显式配置的 binary-path 原值，用于区分"配置生效"与"按平台解析命中"
     * @param baseUrl              现读「模型设置」主模型解析出的端点地址（非密钥，可直接回显）；解析失败为 {@code null}
     * @param model                同上，模型名
     * @param providerName         同上，供应商名
     * @param endpointOk           主模型凭据是否解析成功。<b>故意只回布尔：密钥值绝不进响应体</b>
     * @param sessions             常驻会话，键为 {@code botId:sourceKey}，值为进程是否存活
     */
    public record StatusVO(
            boolean enabled,
            boolean ready,
            String notReadyReason,
            String platformKey,
            String binaryPath,
            boolean binaryExists,
            long binarySizeBytes,
            String configDir,
            boolean configDirExists,
            String workingDir,
            boolean workingDirExists,
            String configuredBinaryPath,
            String baseUrl,
            String model,
            String providerName,
            boolean endpointOk,
            long turnTimeoutSeconds,
            long shutdownGraceMillis,
            boolean warmup,
            List<String> extraArgs,
            Map<String, Boolean> sessions) {
    }

    /**
     * 配置播种检查结果。
     *
     * @param projects 键即被信任的工作区路径，与 {@code .claude.json} 里的 {@code projects} 一一对应
     * @param rawJson  美化后的文件全文
     * @param note     人话说明（文件不存在 / 解析失败 / 未初始化），正常时为 {@code null}
     */
    public record ConfigSeedVO(
            String configDir,
            String filePath,
            boolean exists,
            long sizeBytes,
            String lastModified,
            Boolean hasCompletedOnboarding,
            String theme,
            Map<String, ProjectTrust> projects,
            String rawJson,
            String note) {

        static ConfigSeedVO unavailable(String note) {
            return new ConfigSeedVO(null, null, false, 0L, null, null, null, Map.of(), null, note);
        }
    }

    /** 单个工作区的信任标记；字段缺失为 {@code null}。 */
    public record ProjectTrust(Boolean hasTrustDialogAccepted, Boolean hasCompletedProjectOnboarding) {
    }
}
