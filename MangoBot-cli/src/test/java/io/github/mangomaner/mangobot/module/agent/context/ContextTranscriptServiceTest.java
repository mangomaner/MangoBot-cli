package io.github.mangomaner.mangobot.module.agent.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 转录的截断规则。读取与定位那部分在 infra（见 {@code ClaudeTranscriptTest}），
 * 这里只钉"给前端的副本怎么裁"。
 */
class ContextTranscriptServiceTest {

    // truncate 不碰文件系统，也不需要 manager
    private final ContextTranscriptService service = new ContextTranscriptService(null);
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 截断要**留前缀**，不能整条换成标记。压缩摘要、被压掉的对话正是"信息不全"时最想读的，
     * 整条换成标记等于把内容删了——实测就踩过：8009 字符的压缩摘要在页面上只剩一行"已省略"。
     */
    @Test
    @DisplayName("超长字符串截断但保留可读前缀")
    void truncatesKeepingPrefix() throws Exception {
        String longText = "A".repeat(20000);
        JsonNode in = mapper.readTree("{\"a\":\"" + longText + "\",\"b\":\"短的\"}");

        JsonNode out = service.truncate(in);
        String a = out.path("a").asText();

        assertTrue(a.startsWith("A".repeat(1000)), "前缀要留着，不能整条换掉");
        assertTrue(a.contains("已省略 15904 字符"), "要标明省了多少：" + a.substring(a.length() - 40));
        assertEquals("短的", out.path("b").asText());
    }

    @Test
    @DisplayName("一般的长文本（如压缩摘要）原样保留，不截")
    void keepsOrdinaryLongText() throws Exception {
        // 实测压缩摘要约 8k 字符——这个量级必须原样给前端，前端自己会折叠
        String summary = "A".repeat(8009);
        JsonNode in = mapper.readTree("{\"c\":\"" + summary + "\"}");

        assertEquals(8009, service.truncate(in).path("c").asText().length());
    }

    @Test
    @DisplayName("结构保持合法：嵌套的数组/对象都还在，只有叶子被换")
    void keepsStructure() throws Exception {
        // 用真·超长的（图片 base64 实测几十万字符），别用刚好过线的
        String longText = "A".repeat(30000);
        JsonNode in = mapper.readTree(
                "{\"message\":{\"content\":[{\"type\":\"image\",\"source\":{\"data\":\""
                        + longText + "\"}},{\"type\":\"text\",\"text\":\"在的\"}]}}");

        JsonNode out = service.truncate(in);

        assertTrue(out.path("message").path("content").isArray());
        assertEquals(2, out.path("message").path("content").size());
        assertEquals("image", out.path("message").path("content").get(0).path("type").asText());
        assertTrue(out.path("message").path("content").get(0).path("source").path("data").asText()
                .contains("已省略"), "超长的 base64 要被截掉");
        assertEquals("在的", out.path("message").path("content").get(1).path("text").asText());
    }

    @Test
    @DisplayName("边界长度不截断")
    void leavesBoundaryLengthAlone() throws Exception {
        JsonNode in = mapper.readTree("{\"a\":\"" + "A".repeat(16384) + "\"}");
        assertEquals(16384, service.truncate(in).path("a").asText().length());
        assertFalse(service.truncate(in).path("a").asText().contains("已省略"));
    }
}
