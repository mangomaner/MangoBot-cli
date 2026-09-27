package io.github.mangomaner.mangobot.infra.claude.transcript;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 从转录算上下文用量。
 *
 * <p>这条路是页面默认显示的（不用起进程、不花钱），而算错的表现是**数字看着合理但不对**——
 * 比如把中间那次 API 调用的 usage 也算进来，总量就翻倍，只看界面根本看不出来。
 */
class ClaudeContextUsageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static List<ClaudeTranscriptEntry> entries(String... jsonLines) {
        List<ClaudeTranscriptEntry> out = new ArrayList<>();
        int i = 0;
        for (String line : jsonLines) {
            try {
                out.add(new ClaudeTranscriptEntry(i++, MAPPER.readTree(line).path("type").asText(""),
                        MAPPER.readTree(line)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return out;
    }

    @Test
    @DisplayName("认 assistant 记录的 message.usage —— 转录里没有 result 记录")
    void readsAssistantUsage() {
        List<ClaudeContextUsage.CallUsage> usage = ClaudeContextUsage.byCall(entries(
                """
                {"type":"assistant","timestamp":"2026-09-26T21:08:33Z",
                 "message":{"id":"m1","usage":{"input_tokens":178,"cache_read_input_tokens":26240,
                          "cache_creation_input_tokens":0,"output_tokens":14}}}""",
                """
                {"type":"result","usage":{"input_tokens":99999}}""",
                """
                {"type":"user","timestamp":"t2"}"""));

        assertEquals(1, usage.size(), "result 记录不该被算（转录里本来也没有）");
        ClaudeContextUsage.CallUsage u = usage.get(0);
        assertEquals(178, u.inputTokens());
        assertEquals(26240, u.cacheReadTokens());
        assertEquals(26418, u.total(), "总量 = 新增 + 缓存读 + 缓存写");
        assertEquals(1, u.call());
    }

    /**
     * 转录把同一次调用记两遍（流式那份 + 收尾那份），不去重的话曲线上的点会翻倍——
     * 而界面上看着只是"柱子密了一倍"，很容易当成正常。
     */
    @Test
    @DisplayName("同一次调用记了两遍时按 message.id 去重")
    void dedupesByMessageId() {
        List<ClaudeContextUsage.CallUsage> usage = ClaudeContextUsage.byCall(entries(
                """
                {"type":"assistant","message":{"id":"same","usage":{"input_tokens":100}}}""",
                """
                {"type":"assistant","message":{"id":"same","usage":{"input_tokens":100}}}""",
                """
                {"type":"assistant","message":{"id":"other","usage":{"input_tokens":200}}}"""));

        assertEquals(2, usage.size(), "同一个 message.id 只算一次");
    }

    @Test
    @DisplayName("逐轮编号，按出现顺序")
    void numbersTurnsInOrder() {
        List<ClaudeContextUsage.CallUsage> usage = ClaudeContextUsage.byCall(entries(
                """
                {"type":"assistant","message":{"id":"a","usage":{"input_tokens":1,"cache_read_input_tokens":0,"cache_creation_input_tokens":0}}}""",
                """
                {"type":"assistant","message":{"id":"b","usage":{"input_tokens":2,"cache_read_input_tokens":0,"cache_creation_input_tokens":0}}}"""));

        assertEquals(2, usage.size());
        assertEquals(1, usage.get(0).call());
        assertEquals(2, usage.get(1).call());
        assertEquals(1, usage.get(0).total());
        assertEquals(2, usage.get(1).total());
    }

    @Test
    @DisplayName("缓存写也算进总量——它同样是这一轮送进去的 token")
    void countsCacheCreation() {
        List<ClaudeContextUsage.CallUsage> usage = ClaudeContextUsage.byCall(entries(
                """
                {"type":"assistant","message":{"id":"c","usage":{"input_tokens":100,"cache_read_input_tokens":0,
                 "cache_creation_input_tokens":5000,"output_tokens":9}}}"""));

        assertEquals(5100, usage.get(0).total());
    }

    @Test
    @DisplayName("没有 usage 的记录跳过，不抛异常")
    void skipsRecordWithoutUsage() {
        List<ClaudeContextUsage.CallUsage> usage = ClaudeContextUsage.byCall(entries(
                """
                {"type":"assistant","message":{"id":"d"}}""",
                """
                {"type":"assistant","message":{"id":"e","usage":{"input_tokens":7}}}"""));

        assertEquals(1, usage.size());
        assertEquals(7, usage.get(0).total());
    }

    @Test
    @DisplayName("空转录返回空列表")
    void emptyIsEmpty() {
        assertTrue(ClaudeContextUsage.byCall(List.of()).isEmpty());
    }

    /**
     * 压缩这块要读对：它的 preTokens 就是压缩前的上下文，而**压缩本身不产生 usage**——
     * 所以下一次调用之前，最后一个 usage 仍是压缩前的旧数字。前端靠 record 下标判断这件事，
     * 读错了就会一直显示一个已经过期的"当前上下文"。
     */
    @Test
    @DisplayName("读得出压缩边界上的压前/压后 token 数")
    void readsCompactionBoundary() {
        List<ClaudeContextUsage.Compaction> cs = ClaudeContextUsage.compactions(entries(
                """
                {"type":"assistant","message":{"id":"m1","usage":{"input_tokens":30972}}}""",
                """
                {"type":"system","subtype":"compact_boundary","timestamp":"2026-09-27T14:56:44Z",
                 "compactMetadata":{"trigger":"manual","preTokens":31018,"postTokens":2002,"durationMs":18527}}""",
                """
                {"type":"user","timestamp":"t"}"""));

        assertEquals(1, cs.size());
        ClaudeContextUsage.Compaction c = cs.get(0);
        assertEquals("manual", c.trigger());
        assertEquals(31018, c.preTokens());
        assertEquals(2002, c.postTokens());
        assertEquals(1, c.record(), "要带上记录下标，前端靠它判断压缩是不是发生在最后一次调用之后");
    }

    @Test
    @DisplayName("没有压缩时返回空，不会把普通 system 记录当成压缩")
    void ignoresNonBoundarySystemRecords() {
        assertTrue(ClaudeContextUsage.compactions(entries(
                """
                {"type":"system","subtype":"init"}""",
                """
                {"type":"system","subtype":"status"}""")).isEmpty());
    }
}
