package io.github.mangomaner.mangobot.infra.claude.session;

import io.github.mangomaner.mangobot.infra.claude.ClaudeRuntimeProperties;
import io.github.mangomaner.mangobot.infra.claude.process.ClaudeBinaryResolver;
import io.github.mangomaner.mangobot.infra.claude.process.ClaudeConfigSeeder;
import io.github.mangomaner.mangobot.infra.claude.spec.ClaudeSessionSpec;
import io.github.mangomaner.mangobot.infra.claude.workspace.ClaudeWorkspace;
import io.github.mangomaner.mangobot.utils.FileUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 真刀真枪打一枪：验证一轮对话能拿到完整正文。默认关闭，因为它需要真实的 claude 二进制**和登录态**，
 * 会花钱、会走网络，不适合放进 CI。手动跑：
 *
 * <pre>mvn -Dtest=ClaudeSessionSmokeTest -DfailIfNoTests=false test</pre>
 *
 * <p>人眼看流式过程用 {@code ClaudeSmokeController} 的 SSE 端点；本测试只断言最终产物，给 CI 用。
 */
@Disabled("需要真实 claude 二进制与登录态，默认关闭")
class ClaudeSessionSmokeTest {

    private static final String SESSION_KEY = "junit:smoke";

    @Test
    void returnsFullAssistantTextWithinOneTurn() {
        ClaudeRuntimeProperties properties = new ClaudeRuntimeProperties();
        properties.setTurnTimeoutSeconds(120);

        Optional<Path> binary = ClaudeBinaryResolver.resolve(properties);
        assumeTrue(binary.isPresent(), "未找到 claude 二进制，跳过。查找顺序见 ClaudeBinaryResolver");

        // 与会话的布局一致：工作区根目录 + 每个 key 一个子目录，claude 家目录在它里面
        ClaudeWorkspace workspace = new ClaudeWorkspace(
                FileUtils.getBaseDirectory().resolve("data/claude-work"));
        workspace.ensure(SESSION_KEY);
        ClaudeConfigSeeder.seed(workspace.configDirOf(SESSION_KEY), workspace.dirOf(SESSION_KEY));

        // 端点凭据平时由 ClaudeRuntimeManager 在 spawn 时从「模型设置」现取；这里没起 Spring，
        // 就直接读它注入的同名环境变量，没配齐当没有登录态跳过（和二进制缺失一个处理）。
        Map<String, String> endpointEnv = endpointEnvFromEnvironment();
        assumeTrue(endpointEnv != null,
                "未提供 ANTHROPIC_BASE_URL / ANTHROPIC_AUTH_TOKEN / ANTHROPIC_MODEL，跳过");

        // 一个工具、一个插件、一条 MCP 连接都不给，也不续会话：冒烟测的是"进程能起来、能回一轮"，
        // 多给东西只会让这一轮多背一段没用的上下文。
        ClaudeSessionSpec spec = new ClaudeSessionSpec(
                List.of(), List.of(), List.of(), false, null, null, endpointEnv,
                false, Duration.ofSeconds(120), List.of());
        ClaudeSession session = new ClaudeSession(SESSION_KEY, "smoke", binary.get(), workspace, properties,
                () -> spec);
        try {
            List<ClaudeEvent> events = session.sendTurn("Reply with exactly the word: PONG")
                    .collectList()
                    .block();

            assertNotNull(events, "事件流不应为 null");
            assertFalse(events.isEmpty(), "应至少收到一个事件");
            assertTrue(events.get(events.size() - 1).isResult(),
                    "最后一个事件应是终止的 result；实际为 " + events.get(events.size() - 1).describe());

            String text = events.stream()
                    .filter(ClaudeEvent::isAssistant)
                    .map(ClaudeEvent::assistantText)
                    .collect(Collectors.joining());
            assertFalse(text.isBlank(), "assistant 正文不应为空");
        } finally {
            session.close();
        }
    }

    /** 与 {@code ClaudeRuntimeManager.endpointEnv()} 注进子进程的三项一致；缺任一项返回 null。 */
    private static Map<String, String> endpointEnvFromEnvironment() {
        String baseUrl = System.getenv("ANTHROPIC_BASE_URL");
        String token = System.getenv("ANTHROPIC_AUTH_TOKEN");
        String model = System.getenv("ANTHROPIC_MODEL");
        if (baseUrl == null || token == null || model == null) {
            return null;
        }
        return Map.of(
                "ANTHROPIC_BASE_URL", baseUrl,
                "ANTHROPIC_AUTH_TOKEN", token,
                "ANTHROPIC_MODEL", model);
    }
}
