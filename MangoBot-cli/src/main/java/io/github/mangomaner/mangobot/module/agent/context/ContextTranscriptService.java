package io.github.mangomaner.mangobot.module.agent.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.mangomaner.mangobot.infra.claude.transcript.ClaudeTranscript;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 把 claude 的转录加工成页面能直接用的形状。
 *
 * <p>读取本身在 infra（{@code ClaudeTranscript}）；这里只加一件事：<b>超长字符串换成省略标记</b>，
 * 那是给前端看的取舍，不属于 CLI 的格式知识。
 */
@Service
@Slf4j
public class ContextTranscriptService {

    // 超过这个长度才截断。阈值放得宽是因为一般的长文本（压缩摘要、模型长回复、大段工具输出）
    // 都该原样给前端、由前端折叠；真正要挡的是图片 base64（几十万字符）和病态超长的工具输出
    private static final int MAX_STRING_CHARS = 16384;

    // 截断时保留的前缀。不能整条换成标记——压缩摘要、被压缩掉的对话恰恰是"信息不全"时最想读的
    private static final int KEEP_PREFIX_CHARS = 4096;

    private final ClaudeRuntimeManager runtimeManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public ContextTranscriptService(ClaudeRuntimeManager runtimeManager) {
        this.runtimeManager = runtimeManager;
    }

    /** 列出一个会话可用的转录，新的在前。 */
    public List<ClaudeTranscript.SessionFile> listSessions(String key) {
        return runtimeManager.transcript().listSessions(key);
    }

    /** 读一份转录；sessionId 为空则取最新的一份。找不到返回 null。 */
    public ClaudeTranscript.Transcript load(String key, String sessionId) {
        return runtimeManager.transcript().load(key, sessionId);
    }

    /** 取某个 image 块的原始 base64；{@code inner >= 0} 表示图嵌在 tool_result 里。 */
    public byte[] imageBytes(String key, String sessionId, int recordIndex, int blockIndex, int inner) {
        return runtimeManager.transcript().imageBytes(key, sessionId, recordIndex, blockIndex, inner);
    }

    /** 图片块的 MIME 类型。 */
    public String imageMediaType(String key, String sessionId, int recordIndex, int blockIndex, int inner) {
        return runtimeManager.transcript().imageMediaType(key, sessionId, recordIndex, blockIndex, inner);
    }

    /** 把一条记录里超长的字符串换成 {@code <已省略 N 字符>}；只作用于返回给前端的副本。 */
    public JsonNode truncate(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (text.length() <= MAX_STRING_CHARS) {
                return node;
            }
            return TextNode.valueOf(text.substring(0, KEEP_PREFIX_CHARS)
                    + System.lineSeparator() + "…<已省略 "
                    + (text.length() - KEEP_PREFIX_CHARS) + " 字符>");
        }
        if (node.isObject()) {
            var copy = mapper.createObjectNode();
            node.fields().forEachRemaining(e -> copy.set(e.getKey(), truncate(e.getValue())));
            return copy;
        }
        if (node.isArray()) {
            var copy = mapper.createArrayNode();
            node.forEach(item -> copy.add(truncate(item)));
            return copy;
        }
        return node;
    }
}
