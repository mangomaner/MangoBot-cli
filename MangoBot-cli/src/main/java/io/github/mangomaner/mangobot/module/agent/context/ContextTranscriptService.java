package io.github.mangomaner.mangobot.module.agent.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 读某个会话的 claude 转录，供"查看上下文"页面用。
 *
 * <p>转录是 claude 自己写的，位置在 {@code <会话目录>/claude-config/projects/<slug>/<sessionId>.jsonl}。
 * 路径拼接复用 {@link ClaudeRuntimeManager#sessionConfigDir(String)}，不在这里重写一遍。
 */
@Service
@Slf4j
public class ContextTranscriptService {

    /**
     * 超过这个长度的字符串才会被截断。
     *
     * <p>阈值放得比较宽：一般的长文本——**压缩摘要**（实测 8k 字符）、模型的长回复、
     * 大段工具输出——都该原样给前端，前端自己会折叠。真正要挡的是图片 base64
     * （实测几十万字符）和病态超长的工具输出。
     */
    private static final int MAX_STRING_CHARS = 16384;

    /**
     * 截断时保留的前缀长度。
     *
     * <p><b>不能整条换成标记</b>：那样等于把内容删了。压缩摘要、被压缩掉的对话，
     * 恰恰是"信息不全"时最想读的东西。留一段能读的，剩下的给个字数。
     */
    private static final int KEEP_PREFIX_CHARS = 4096;

    private final ClaudeRuntimeManager runtimeManager;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 解析结果按 (路径, mtime, size) 缓存——转录能有十几 MB，每次请求都重读受不了。 */
    private final Map<String, CachedTranscript> cache = new ConcurrentHashMap<>();

    public ContextTranscriptService(ClaudeRuntimeManager runtimeManager) {
        this.runtimeManager = runtimeManager;
    }

    /**
     * 会话键，必须和 {@code ClaudeRuntimeManager.getOrCreate} 拼出来的那个一致，否则找不到转录。
     *
     * <p>它拼的是 {@code botId + ":" + sourceKey}，而 sourceKey 因来源而异：
     * 群聊/私聊是 {@code group:群号}（见 {@code ChatTarget.runtimeSourceKey()}），
     * web 会话直接就是会话 id（见 {@code ChatController} 里传的 {@code String.valueOf(sessionId)}）。
     * 所以 web 的键是 {@code web:<会话id>}，不是 {@code web:web:<会话id>}。
     */
    public static String sessionKey(String botId, String source, String targetId) {
        return SessionSource.WEB.getSourceKey().equals(source)
                ? botId + ":" + targetId
                : botId + ":" + source + ":" + targetId;
    }

    /**
     * 列出一个会话可用的转录，新的在前。
     *
     * <p>可能有多个：进程重建（换模型、清上下文）会开新 sessionId，旧文件留在原地。
     */
    public List<SessionFile> listSessions(String key) {
        Path projectsDir = runtimeManager.sessionConfigDir(key).resolve("projects");
        List<SessionFile> out = new ArrayList<>();
        for (Path file : findTranscripts(projectsDir)) {
            out.add(new SessionFile(stripExt(file.getFileName().toString()),
                    sizeOf(file), mtimeOf(file)));
        }
        out.sort(Comparator.comparingLong(SessionFile::modifiedAt).reversed());
        return out;
    }

    /** 读一份转录；sessionId 为空则取最新的一份。找不到返回 null。 */
    public Transcript load(String key, String sessionId) {
        Path file = resolveFile(key, sessionId);
        if (file == null) {
            return null;
        }
        try {
            long mtime = Files.getLastModifiedTime(file).toMillis();
            long size = Files.size(file);
            CachedTranscript cached = cache.get(file.toString());
            if (cached != null && cached.mtime == mtime && cached.size == size) {
                return cached.transcript;
            }
            Transcript parsed = parse(file, stripExt(file.getFileName().toString()), size);
            cache.put(file.toString(), new CachedTranscript(mtime, size, parsed));
            return parsed;
        } catch (IOException e) {
            log.warn("读转录失败：{}", file, e);
            return null;
        }
    }

    /**
     * 取某个 image 块的原始 base64。
     *
     * <p>{@code inner} 是给"图嵌在 tool_result 里"那种情况用的：工具返回的图片在
     * {@code tool_result.content} 这个内层数组里（例如 {@code list_memes} 一次返回好几张），
     * 传 -1 表示图直接在记录的 content 数组里。
     */
    public byte[] imageBytes(String key, String sessionId, int recordIndex, int blockIndex, int inner) {
        JsonNode block = imageBlock(key, sessionId, recordIndex, blockIndex, inner);
        if (block == null) {
            return null;
        }
        String data = block.path("source").path("data").asText("");
        try {
            return java.util.Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            log.warn("图片 base64 解不开：sessionKey={} record={} block={} inner={}",
                    key, recordIndex, blockIndex, inner);
            return null;
        }
    }

    /** 图片块的 MIME 类型；取不到时按 jpeg 兜底。 */
    public String imageMediaType(String key, String sessionId, int recordIndex, int blockIndex, int inner) {
        JsonNode block = imageBlock(key, sessionId, recordIndex, blockIndex, inner);
        String type = block == null ? "" : block.path("source").path("media_type").asText("");
        return type.isBlank() ? "image/jpeg" : type;
    }

    /** 定位到那个 image 块；不是图片或路径不存在时返回 null。 */
    private JsonNode imageBlock(String key, String sessionId, int recordIndex, int blockIndex, int inner) {
        Transcript transcript = load(key, sessionId);
        if (transcript == null || recordIndex < 0 || recordIndex >= transcript.records().size()) {
            return null;
        }
        JsonNode content = transcript.records().get(recordIndex).json().path("message").path("content");
        return imageBlockIn(content, blockIndex, inner);
    }

    /**
     * 在一条记录的 content 数组里定位 image 块。
     *
     * <p>{@code inner >= 0} 表示图嵌在 {@code tool_result.content} 里 —— 工具一次返回好几张图时
     * 就是这样（{@code list_memes} 最多一次给 10 张）。只在外层找的话这类图一张都取不到。
     */
    static JsonNode imageBlockIn(JsonNode content, int blockIndex, int inner) {
        if (content == null || !content.isArray() || blockIndex < 0 || blockIndex >= content.size()) {
            return null;
        }
        JsonNode block = content.get(blockIndex);
        if (inner >= 0) {
            JsonNode nested = block.path("content");
            if (!nested.isArray() || inner >= nested.size()) {
                return null;
            }
            block = nested.get(inner);
        }
        return "image".equals(block.path("type").asText()) ? block : null;
    }

    /**
     * 把一条记录里超长的字符串换成 {@code <已省略 N 字符>}。
     *
     * <p>只作用于返回给前端的副本；出图走 {@link #imageBytes}，那边拿的是未截断的原数据。
     */
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

    // ========================================================================

    private Path resolveFile(String key, String sessionId) {
        List<Path> all = findTranscripts(runtimeManager.sessionConfigDir(key).resolve("projects"));
        if (all.isEmpty()) {
            return null;
        }
        if (sessionId == null || sessionId.isBlank()) {
            return all.stream()
                    .max(Comparator.comparingLong(ContextTranscriptService::mtimeOf))
                    .orElse(null);
        }
        return all.stream()
                .filter(p -> stripExt(p.getFileName().toString()).equals(sessionId))
                .findFirst()
                .orElse(null);
    }

    private Transcript parse(Path file, String sessionId, long size) throws IOException {
        List<Record> records = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int index = 0;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            try {
                JsonNode node = mapper.readTree(line);
                records.add(new Record(index++, node.path("type").asText(""), node));
            } catch (Exception e) {
                // 坏行跳过，不让一行坏数据毁掉整个页面
                log.warn("转录里有解析不了的行，已跳过：{}", file);
            }
        }
        return new Transcript(sessionId, size, lines.size(), records);
    }

    /**
     * 找一个会话下的全部转录文件。
     *
     * <p>层级是 {@code projects/<cwd-slug>/<sessionId>.jsonl}，slug 由 claude 按 cwd 生成，
     * 这里不猜它怎么拼，直接通配一层目录。
     */
    static List<Path> findTranscripts(Path projectsDir) {
        if (!Files.isDirectory(projectsDir)) {
            return List.of();
        }
        List<Path> out = new ArrayList<>();
        try (Stream<Path> slugs = Files.list(projectsDir)) {
            for (Path slug : slugs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> files = Files.list(slug)) {
                    out.addAll(files.filter(ContextTranscriptService::isTranscript).toList());
                }
            }
        } catch (IOException e) {
            log.warn("找转录失败：{}", projectsDir, e);
            return List.of();
        }
        return out;
    }

    private static boolean isTranscript(Path p) {
        return Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jsonl");
    }

    private static String stripExt(String name) {
        return name.endsWith(".jsonl") ? name.substring(0, name.length() - ".jsonl".length()) : name;
    }

    private static long mtimeOf(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0L;
        }
    }

    private record CachedTranscript(long mtime, long size, Transcript transcript) {
    }

    public record SessionFile(String sessionId, long size, long modifiedAt) {
    }

    public record Record(int index, String type, JsonNode json) {
    }

    public record Transcript(String sessionId, long fileSize, int lineCount, List<Record> records) {
    }
}
