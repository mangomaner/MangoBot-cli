package io.github.mangomaner.mangobot.module.agent.runtime.claude;

import io.github.mangomaner.mangobot.utils.FileUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
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

    @Test
    void returnsFullAssistantTextWithinOneTurn() {
        ClaudeRuntimeProperties properties = new ClaudeRuntimeProperties();
        properties.setTurnTimeoutSeconds(120);

        Optional<Path> binary = ClaudeBinaryResolver.resolve(properties);
        assumeTrue(binary.isPresent(), "未找到 claude 二进制，跳过。查找顺序见 ClaudeBinaryResolver");

        Path configDir = FileUtils.getBaseDirectory().resolve("data/claude-config");
        Path workingDir = FileUtils.getBaseDirectory().resolve("data/claude-work/junit-smoke");
        ClaudeConfigSeeder.seed(configDir, workingDir);

        ClaudeSession session = new ClaudeSession("junit:smoke", binary.get(), workingDir, configDir, properties);
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
}
