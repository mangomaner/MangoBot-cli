package io.github.mangomaner.mangobot.infra.claude.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 播种 {@code .claude.json} 的行为。
 *
 * <p>它现在是"每次拉起进程之前"调的（每个会话一份 config 目录），所以两件事必须成立：
 * 缺了它进程会卡在 onboarding；重复调不能把 claude 自己写进去的字段冲掉。
 * 后者还兼着"清空上下文后自愈"这条：删掉 config 目录 → 下一轮重新播种。
 */
class ClaudeConfigSeederTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("全新目录：种出 onboarding 标记与工作区信任，缺了前者进程会卡住")
    void seedsFreshDir(@TempDir Path tmp) throws Exception {
        Path configDir = tmp.resolve("claude-config");
        Path workspace = tmp.resolve("work").resolve("3970521445_group_1");

        ClaudeConfigSeeder.seed(configDir, workspace);

        Path file = configDir.resolve(".claude.json");
        assertTrue(Files.isRegularFile(file), "配置文件没生成");
        JsonNode root = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
        assertTrue(root.path("hasCompletedOnboarding").asBoolean(), "onboarding 标记没写上");
        assertEquals("dark", root.path("theme").asText());
        assertTrue(root.path("projects").path(workspace.toAbsolutePath().normalize().toString())
                .path("hasTrustDialogAccepted").asBoolean(), "工作区信任没写上");
    }

    @Test
    @DisplayName("重复播种：保留 claude 自己写进去的字段（它是合并，不是覆盖）")
    void reseedKeepsForeignFields(@TempDir Path tmp) throws Exception {
        Path configDir = tmp.resolve("claude-config");
        Path workspace = tmp.resolve("work").resolve("session-a");
        ClaudeConfigSeeder.seed(configDir, workspace);

        // 模拟 claude 自己往这份配置里记账
        Path file = configDir.resolve(".claude.json");
        var node = (com.fasterxml.jackson.databind.node.ObjectNode)
                MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
        node.put("userID", "abc123");
        node.putObject("skillUsage").putObject("group:mangoman-persona").put("usageCount", 6);
        Files.writeString(file, node.toString());

        ClaudeConfigSeeder.seed(configDir, workspace);

        JsonNode after = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
        assertEquals("abc123", after.path("userID").asText(), "claude 的记账被冲掉了");
        assertEquals(6, after.path("skillUsage").path("group:mangoman-persona").path("usageCount").asInt());
        assertTrue(after.path("hasCompletedOnboarding").asBoolean());
    }

    @Test
    @DisplayName("清空上下文之后自愈：config 目录被删掉，再播一次照样能起来")
    void reseedsAfterDirectoryRemoved(@TempDir Path tmp) throws Exception {
        Path configDir = tmp.resolve("claude-config");
        Path workspace = tmp.resolve("work").resolve("session-b");
        ClaudeConfigSeeder.seed(configDir, workspace);
        assertTrue(Files.isRegularFile(configDir.resolve(".claude.json")));

        // 「清空上下文」= 把这个目录整个删掉
        Files.delete(configDir.resolve(".claude.json"));
        Files.delete(configDir);

        ClaudeConfigSeeder.seed(configDir, workspace);

        assertTrue(Files.isRegularFile(configDir.resolve(".claude.json")), "删完没被重新播种");
        JsonNode root = MAPPER.readTree(
                Files.readString(configDir.resolve(".claude.json"), StandardCharsets.UTF_8));
        assertTrue(root.path("hasCompletedOnboarding").asBoolean());
    }

    @Test
    @DisplayName("坏文件不致命：内容不是 JSON 时重建，而不是抛出去把这一轮打挂")
    void rebuildsBrokenFile(@TempDir Path tmp) throws Exception {
        Path configDir = tmp.resolve("claude-config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve(".claude.json"), "{ 这不是 json", StandardCharsets.UTF_8);

        ClaudeConfigSeeder.seed(configDir, tmp.resolve("work").resolve("s"));

        JsonNode root = MAPPER.readTree(
                Files.readString(configDir.resolve(".claude.json"), StandardCharsets.UTF_8));
        assertTrue(root.path("hasCompletedOnboarding").asBoolean());
        assertFalse(root.path("hasCompletedOnboarding").isMissingNode());
    }
}
