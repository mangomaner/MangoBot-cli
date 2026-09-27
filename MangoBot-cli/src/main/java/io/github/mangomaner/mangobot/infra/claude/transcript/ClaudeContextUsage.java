package io.github.mangomaner.mangobot.infra.claude.transcript;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 从转录里算出上下文用量。
 *
 * <p><b>认的是 {@code assistant} 记录，不是 {@code result}</b>——转录里压根没有 {@code result}：
 * 那个事件只出现在实时的 stdout 流里，不落盘。{@code message.usage} 才是每次 API 调用的真实用量，
 * {@code input + cache_read + cache_creation} 就是那一刻模型看到的上下文总量。
 */
public final class ClaudeContextUsage {

    private ClaudeContextUsage() {
    }

    /** 一次模型调用的上下文用量；{@code total} 就是那一刻模型看到的上下文总量。 */
    public record CallUsage(int call, int record, String timestamp, long inputTokens,
                            long cacheReadTokens, long cacheCreationTokens, long total,
                            long outputTokens) {
    }

    /** 转录里的一次压缩。CLI 在 {@code compact_boundary} 记录上留了压前压后的 token 数。 */
    public record Compaction(int record, String timestamp, String trigger,
                             long preTokens, long postTokens) {
    }

    /**
     * 逐次调用的用量。
     *
     * <p>按 {@code message.id} 去重：转录把同一次调用记了两遍（流式那份 + 收尾那份），不去重的话
     * 曲线上的点会翻倍。
     */
    public static List<CallUsage> byCall(List<ClaudeTranscriptEntry> entries) {
        List<CallUsage> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ClaudeTranscriptEntry entry : entries) {
            if (!"assistant".equals(entry.type())) {
                continue;
            }
            JsonNode message = entry.json().path("message");
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
            out.add(new CallUsage(out.size() + 1, entry.index(),
                    entry.json().path("timestamp").asText(""),
                    input, cacheRead, cacheCreate,
                    input + cacheRead + cacheCreate,
                    usage.path("output_tokens").asLong()));
        }
        return out;
    }

    /**
     * 转录里发生过的压缩。
     *
     * <p>为什么重要：压缩之后上下文会掉一大截，但<b>压缩本身不产生新的 usage</b>。所以在下一次模型
     * 调用发生之前，最后一次调用的 usage 仍是压缩前的旧数字——拿它当"当前上下文"会显示一个已经过期
     * 的值（实测：压缩前 31k、压缩后实际 12k，页面上却还写着 31k）。
     */
    public static List<Compaction> compactions(List<ClaudeTranscriptEntry> entries) {
        List<Compaction> out = new ArrayList<>();
        for (ClaudeTranscriptEntry entry : entries) {
            if (!"compact_boundary".equals(entry.json().path("subtype").asText(""))) {
                continue;
            }
            JsonNode meta = entry.json().path("compactMetadata");
            out.add(new Compaction(entry.index(),
                    entry.json().path("timestamp").asText(""),
                    meta.path("trigger").asText(""),
                    meta.path("preTokens").asLong(),
                    meta.path("postTokens").asLong()));
        }
        return out;
    }

    /**
     * 最近一次模型调用的上下文大小；读不到返回 0。
     *
     * <p>给"该不该压缩"的判断用，和页面上显示的是同一个数——两边各算各的会出现"页面说 12k、
     * 后台却按 31k 去压"这种事。
     */
    public static long latestTotal(ClaudeTranscript.Transcript transcript) {
        if (transcript == null) {
            return 0L;
        }
        List<CallUsage> usage = byCall(transcript.entries());
        return usage.isEmpty() ? 0L : usage.get(usage.size() - 1).total();
    }
}
