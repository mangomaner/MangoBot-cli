package io.github.mangomaner.mangobot.module.agent.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.api.MangoModelApi;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeProperties;
import io.github.mangomaner.mangobot.module.configuration.model.domain.ModelEndpoint;
import io.github.mangomaner.mangobot.api.enums.ModelRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 上下文用量分析，两种口径。
 *
 * <h3>自己算（默认走这条）</h3>
 * 转录里每个 {@code result} 事件都带 {@code usage}，其中
 * {@code input + cache_read + cache_creation} 就是**那一轮真实的上下文大小**。
 * 所以"现在多大、逐轮怎么涨的"完全算得出来，不用起进程、不花钱。
 * 拿不到的是分类（系统提示词/工具/技能各占多少）——那是 CLI 内部的估算，转录里没有。
 *
 * <h3>问 CLI（按需，靠按钮触发）</h3>
 * {@code claude -p "/context"} 能给出分类明细，但实测它**会把这个命令和输出当成一轮对话
 * 追加进转录**——对活跃会话跑就是往 bot 的上下文里塞垃圾。所以这里**先复制一份再跑**，
 * 跑完立刻删。代价是一次本地目录拷贝。
 */
@Service
@Slf4j
public class ContextAnalysisService {

    /** 跑 /context 的超时。它是纯本地计算，正常几秒内就回。 */
    private static final long REPORT_TIMEOUT_SECONDS = 60L;

    private final ContextTranscriptService transcriptService;
    private final ClaudeRuntimeManager runtimeManager;
    private final ClaudeRuntimeProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public ContextAnalysisService(ContextTranscriptService transcriptService,
                                  ClaudeRuntimeManager runtimeManager,
                                  ClaudeRuntimeProperties properties) {
        this.transcriptService = transcriptService;
        this.runtimeManager = runtimeManager;
        this.properties = properties;
    }

    /** 逐次的上下文大小，从转录里的 usage 算出来。 */
    public List<CallUsage> usageByCall(String sessionKey, String sessionId) {
        ContextTranscriptService.Transcript transcript = transcriptService.load(sessionKey, sessionId);
        return transcript == null ? List.of() : collectUsage(transcript.records());
    }

    /**
     * 从转录里挑出每次模型调用的用量。
     *
     * <p><b>认的是 {@code assistant} 记录，不是 {@code result}</b> —— 转录里压根没有 {@code result}：
     * 那个事件只出现在实时的 stdout 流里，不落盘。{@code assistant} 记录的 {@code message.usage}
     * 才是每次 API 调用的真实用量。
     *
     * <p>{@code input + cache_read + cache_creation} 就是那次调用送进去的全部 token，
     * 也就是模型当时看到的上下文总量。这一串数字连起来就是上下文的增长曲线。
     *
     * <p>按 {@code message.id} 去重：转录把同一次调用记了两遍（流式那份 + 收尾那份），
     * 不去重的话曲线上的点会翻倍。
     */
    static List<CallUsage> collectUsage(List<ContextTranscriptService.Record> records) {
        List<CallUsage> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ContextTranscriptService.Record record : records) {
            if (!"assistant".equals(record.type())) {
                continue;
            }
            JsonNode message = record.json().path("message");
            JsonNode usage = message.path("usage");
            if (usage.isMissingNode() || usage.isNull()) {
                continue;
            }
            String id = message.path("id").asText("");
            if (!id.isEmpty() && !seen.add(id)) {
                continue;
            }
            long input = usage.path("input_tokens").asLong();
            long cacheRead = usage.path("cache_read_input_tokens").asLong();
            long cacheCreate = usage.path("cache_creation_input_tokens").asLong();
            out.add(new CallUsage(out.size() + 1,
                    record.json().path("timestamp").asText(""),
                    input, cacheRead, cacheCreate,
                    input + cacheRead + cacheCreate,
                    usage.path("output_tokens").asLong()));
        }
        return out;
    }

    /**
     * 让 CLI 给出上下文分类明细。
     *
     * <p><b>为什么复制</b>：实测 {@code -c} 会把 {@code /context} 这条命令连同输出写进转录。
     * 直接对活跃会话跑，等于往 bot 的对话里插一轮它自己没说过的话。复制一份到临时目录，
     * 在副本上跑，无论 CLI 写什么都在副本里，跑完删掉。
     *
     * @return CLI 输出的 markdown；跑不了（二进制缺失、模型没配好、超时）返回一段可读的说明
     */
    public String reportFromCli(String sessionKey, SessionSource source, String sessionId) {
        Path binary = runtimeManager.binaryPath();
        if (binary == null || !Files.isExecutable(binary)) {
            return "跑不了：claude 可执行文件不可用。";
        }
        ModelEndpoint endpoint = MangoModelApi.resolveEndpoint(ModelRole.MAIN);
        if (!endpoint.ok()) {
            return "跑不了：主模型不可用（" + endpoint.problem() + "）。";
        }

        Path configDir = runtimeManager.sessionConfigDir(sessionKey);
        if (!Files.isDirectory(configDir)) {
            return "这个会话还没有上下文目录（没跑过任何一轮）。";
        }
        Path workDir = configDir.getParent();
        Path tempRoot = null;
        try {
            tempRoot = Files.createTempDirectory("mango-ctx-");
            Path tempConfig = tempRoot.resolve("claude-config");
            copyTree(configDir, tempConfig);

            List<String> command = new ArrayList<>(List.of(
                    binary.toString(), "-p", "/context", "--output-format", "json"));
            // 和真实会话同一套参数（技能目录、系统提示词、MCP、工具白名单）——
            // 少给一个，下面那份报告算的就是另一套配置的占比，看着像真的却是错的。
            command.addAll(runtimeManager.capabilityArgs(sessionKey, source));
            command.add("--tools");
            command.add(properties.getBuiltinTools() == null ? "" : properties.getBuiltinTools());
            command.add("-c");

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workDir.toFile());
            builder.environment().putAll(envOf(endpoint));
            // 指向副本：CLI 要往转录里写什么，都写进副本
            builder.environment().put("CLAUDE_CONFIG_DIR", tempConfig.toString());

            Process process = builder.start();
            String stdout = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(REPORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "跑超时了（" + REPORT_TIMEOUT_SECONDS + " 秒）。";
            }
            return extractReport(stdout);
        } catch (Exception e) {
            log.warn("跑 /context 失败：session={}", sessionKey, e);
            return "跑失败了：" + e.getMessage();
        } finally {
            deleteQuietly(tempRoot);
        }
    }

    /** 从 {@code {"result": "## Context Usage ..."}} 里取出报告正文。 */
    private String extractReport(String stdout) {
        try {
            JsonNode root = mapper.readTree(stdout);
            String result = root.path("result").asText("");
            return result.isBlank() ? "CLI 没有返回内容。" : result;
        } catch (Exception e) {
            return stdout.isBlank() ? "CLI 没有返回内容。" : stdout;
        }
    }

    private Map<String, String> envOf(ModelEndpoint endpoint) {
        return Map.of(
                "ANTHROPIC_BASE_URL", endpoint.baseUrl(),
                "ANTHROPIC_AUTH_TOKEN", endpoint.apiKey(),
                "ANTHROPIC_MODEL", endpoint.modelName());
    }

    private static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteQuietly(Path root) {
        if (root == null) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 删不掉就算了，临时目录，不打断请求
                }
            });
        } catch (IOException e) {
            log.warn("临时上下文目录没删干净：{}", root, e);
        }
    }

    /** 一次模型调用的上下文用量。{@code total} 就是那一刻模型看到的上下文总量。 */
    public record CallUsage(int call, String timestamp, long inputTokens, long cacheReadTokens,
                            long cacheCreationTokens, long total, long outputTokens) {
    }
}
