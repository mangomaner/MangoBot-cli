package io.github.mangomaner.mangobot.infra.claude.transcript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 转录读取里两段纯逻辑：找文件、定位图片块。读文件本身要真目录，走端到端验。 */
class ClaudeTranscriptTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("在 projects/<slug>/ 下找 .jsonl，忽略别的文件")
    void findsTranscriptsUnderSlugDirs(@TempDir Path projects) throws Exception {
        Path slug = Files.createDirectories(projects.resolve("C--some-slug"));
        Files.writeString(slug.resolve("s1.jsonl"), "{}");
        Files.writeString(slug.resolve("s2.jsonl"), "{}");
        Files.writeString(slug.resolve("notes.txt"), "x");       // 不是转录
        Files.createDirectories(slug.resolve("subdir"));          // 目录不算

        List<String> names = ClaudeTranscript.findTranscripts(projects).stream()
                .map(p -> p.getFileName().toString()).sorted().toList();

        assertEquals(List.of("s1.jsonl", "s2.jsonl"), names);
    }

    @Test
    @DisplayName("目录不存在时返回空列表，不抛异常")
    void missingDirIsNotAnError(@TempDir Path tmp) {
        assertTrue(ClaudeTranscript.findTranscripts(tmp.resolve("nope")).isEmpty());
    }

    @Test
    @DisplayName("图直接在 content 里时，inner=-1 就能取到")
    void findsImageDirectlyInContent() throws Exception {
        JsonNode content = mapper.readTree("""
                [{"type":"text","text":"看图"},
                 {"type":"image","source":{"media_type":"image/png","data":"AAAA"}}]""");

        JsonNode block = ClaudeTranscript.imageBlockIn(content, 1, -1);

        assertEquals("image/png", block.path("source").path("media_type").asText());
    }

    /**
     * 工具一次返回多张图时，图在 tool_result.content 这个内层数组里（list_memes 就是这样）。
     * 只在外层找会返回 null，表现为"页面上一张图都显示不出来"。
     */
    @Test
    @DisplayName("图嵌在 tool_result 里时，靠 inner 定位")
    void findsImageNestedInToolResult() throws Exception {
        JsonNode content = mapper.readTree("""
                [{"type":"tool_result","content":[
                    {"type":"text","text":"[1] fileId=abc"},
                    {"type":"image","source":{"media_type":"image/gif","data":"BBBB"}},
                    {"type":"image","source":{"media_type":"image/jpeg","data":"CCCC"}}]}]""");

        assertEquals("image/gif",
                ClaudeTranscript.imageBlockIn(content, 0, 1).path("source").path("media_type").asText());
        assertEquals("image/jpeg",
                ClaudeTranscript.imageBlockIn(content, 0, 2).path("source").path("media_type").asText());
        // 不传 inner 时外层那个是 tool_result，不是图片
        assertNull(ClaudeTranscript.imageBlockIn(content, 0, -1));
    }

    @Test
    @DisplayName("越界或指到非图片都返回 null，不抛异常")
    void outOfRangeIsNull() throws Exception {
        JsonNode content = mapper.readTree("[{\"type\":\"text\",\"text\":\"x\"}]");

        assertNull(ClaudeTranscript.imageBlockIn(content, 0, -1));   // 指到 text
        assertNull(ClaudeTranscript.imageBlockIn(content, 5, -1));   // 外层越界
        assertNull(ClaudeTranscript.imageBlockIn(content, 0, 9));    // 内层越界
        assertNull(ClaudeTranscript.imageBlockIn(null, 0, -1));
    }
}
