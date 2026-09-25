package io.github.mangomaner.mangobot.module.agent.runtime.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * claude 进程输出的一行 stream-json。
 *
 * <p>刻意不加强类型模型：只保留原始 {@link JsonNode}，需要什么字段现取现用。
 * 这样后续"让 claude 返回提示、再本地调 LLM"的回复语义定案时，不会被这里的类型锁死。
 *
 * <p>已知的顶层 {@code type}：{@code system}（含 init）、{@code assistant}、{@code user}、
 * {@code result}（每轮终止事件，带 cost/duration/subtype），以及开启
 * {@code --include-partial-messages} 后的 {@code stream_event} 增量。
 */
public record ClaudeEvent(JsonNode payload, long receivedAtMillis) {

    public static final String TYPE_SYSTEM = "system";
    public static final String TYPE_ASSISTANT = "assistant";
    public static final String TYPE_USER = "user";
    public static final String TYPE_RESULT = "result";
    public static final String TYPE_STREAM_EVENT = "stream_event";

    public static final String EVENT_CONTENT_BLOCK_START = "content_block_start";
    public static final String EVENT_CONTENT_BLOCK_DELTA = "content_block_delta";
    public static final String EVENT_CONTENT_BLOCK_STOP = "content_block_stop";

    private static final JsonNode EMPTY_ARRAY = JsonNodeFactory.instance.arrayNode();

    /** 顶层 type，缺失时返回空串。 */
    public String type() {
        return payload.path("type").asText("");
    }

    /** 顶层 subtype，缺失时返回空串。 */
    public String subtype() {
        return payload.path("subtype").asText("");
    }

    /** 是否为每轮的终止事件。收到它即代表本轮结束，可以发下一轮。 */
    public boolean isResult() {
        return TYPE_RESULT.equals(type());
    }

    public boolean isSystem() {
        return TYPE_SYSTEM.equals(type());
    }

    public boolean isAssistant() {
        return TYPE_ASSISTANT.equals(type());
    }

    public boolean isUser() {
        return TYPE_USER.equals(type());
    }

    /** 是否为 {@code --include-partial-messages} 打开后逐块吐出的增量事件。 */
    public boolean isStreamEvent() {
        return TYPE_STREAM_EVENT.equals(type());
    }

    /**
     * {@code stream_event} 的 {@code event.type}，取值见 {@code content_block_start} 等常量；
     * 非增量事件返回空串。
     */
    public String streamEventType() {
        return payload.path("event").path("type").asText("");
    }

    /** {@code stream_event} 的内容块序号（{@code event.index}）；缺失返回 -1。 */
    public int streamEventIndex() {
        return payload.path("event").path("index").asInt(-1);
    }

    /** {@code content_block_start} 里那一块的类型（{@code text} / {@code thinking} / {@code tool_use}）。 */
    public String streamBlockType() {
        return payload.path("event").path("content_block").path("type").asText("");
    }

    /** {@code message.content} 块数组；非消息类事件返回空数组。 */
    public JsonNode contentBlocks() {
        JsonNode content = payload.path("message").path("content");
        return content.isArray() ? content : EMPTY_ARRAY;
    }

    /** 消息里所有 {@code tool_use} 块；非消息类事件返回空列表。 */
    public List<JsonNode> toolUseBlocks() {
        return blocksOfType("tool_use");
    }

    /** 消息里所有 {@code tool_result} 块（claude 把它们放在一个 {@code user} 事件里回传）。 */
    public List<JsonNode> toolResultBlocks() {
        return blocksOfType("tool_result");
    }

    /** {@code result} 事件的 token 用量对象；缺失时返回 missing node（{@link JsonNode#isMissingNode()}）。 */
    public JsonNode usage() {
        return payload.path("usage");
    }

    /** 拼接本轮 assistant 的全部 text 块。 */
    public String assistantText() {
        return concatBlocks("text", "text");
    }

    /** 拼接本轮 assistant 的全部 thinking 块。 */
    public String assistantThinking() {
        return concatBlocks("thinking", "thinking");
    }

    /** {@code stream_event} 里的正文增量（{@code text_delta}）；非增量事件返回空串。 */
    public String deltaText() {
        return deltaField("text");
    }

    /** {@code stream_event} 里的思考增量（{@code thinking_delta}）；非增量事件返回空串。 */
    public String deltaThinking() {
        return deltaField("thinking");
    }

    /** {@code result} 事件的客户端成本估算，缺失时返回空串。 */
    public String costUsd() {
        return payload.path("total_cost_usd").asText("");
    }

    /** {@code result} 事件的耗时（毫秒），缺失时返回 0。 */
    public long durationMs() {
        return payload.path("duration_ms").asLong(0L);
    }

    /** {@code result} 事件的结束原因，如 {@code success} / {@code error_max_turns}。 */
    public String resultSubtype() {
        return isResult() ? subtype() : "";
    }

    public OffsetDateTime receivedAt() {
        return OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(receivedAtMillis), ZoneId.systemDefault());
    }

    /** 一行摘要，供 smoke 端点逐事件打印。 */
    public String describe() {
        return switch (type()) {
            case TYPE_RESULT -> "result subtype=" + subtype()
                    + " costUsd=" + costUsd()
                    + " durationMs=" + durationMs();
            case TYPE_ASSISTANT -> "assistant blocks=" + contentBlocks().size()
                    + " textChars=" + assistantText().length()
                    + " thinkingChars=" + assistantThinking().length();
            case TYPE_SYSTEM -> "system subtype=" + subtype();
            default -> subtype().isEmpty() ? type() : type() + " subtype=" + subtype();
        };
    }

    /**
     * 增量文本在 {@code payload.event.delta} 下——注意<b>不在</b> {@code payload.message.content}，
     * 所以 {@link #assistantText()} 这类按 {@code message.content} 取块的方法对 {@code stream_event}
     * 一律返回空串。实测形状（v2.1.128）：
     * <pre>
     * {"type":"stream_event",
     *  "event":{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"P"}},…}
     * </pre>
     */
    private String deltaField(String field) {
        return payload.path("event").path("delta").path(field).asText("");
    }

    private String concatBlocks(String blockType, String textField) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : contentBlocks()) {
            if (blockType.equals(block.path("type").asText(""))) {
                sb.append(block.path(textField).asText(""));
            }
        }
        return sb.toString();
    }

    /** 按块类型挑出 {@code message.content} 里的块，保持原有顺序。 */
    private List<JsonNode> blocksOfType(String blockType) {
        List<JsonNode> blocks = new ArrayList<>();
        for (JsonNode block : contentBlocks()) {
            if (blockType.equals(block.path("type").asText(""))) {
                blocks.add(block);
            }
        }
        return blocks;
    }
}
