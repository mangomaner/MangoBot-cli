package io.github.mangomaner.mangobot.infra.claude.transcript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.infra.claude.workspace.ClaudeWorkspace;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 读 claude 自己写的转录，供"查看上下文"页面用。
 *
 * <p>位置是 {@code <会话目录>/claude-config/projects/<slug>/<sessionId>.jsonl}。slug 由 claude 按 cwd
 * 生成，规则没公开文档，所以这里不猜它怎么拼，直接通配一层目录。
 *
 * <p>解析结果按 (路径, mtime, size) 缓存——转录能有十几 MB，每次请求都重读受不了。
 */
@Slf4j
public class ClaudeTranscript {

    private final ClaudeWorkspace workspace;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public ClaudeTranscript(ClaudeWorkspace workspace) {
        this.workspace = workspace;
    }

    /**
     * 列出一个会话可用的转录，新的在前。
     *
     * <p>可能有多个：进程重建（换模型、清上下文）会开新 sessionId，旧文件留在原地。
     */
    public List<SessionFile> listSessions(String key) {
        List<SessionFile> out = new ArrayList<>();
        for (Path file : findTranscripts(workspace.projectsDirOf(key))) {
            out.add(new SessionFile(stripExt(file.getFileName().toString()), sizeOf(file), mtimeOf(file)));
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
            Cached cached = cache.get(file.toString());
            if (cached != null && cached.mtime == mtime && cached.size == size) {
                return cached.transcript;
            }
            Transcript parsed = parse(file, stripExt(file.getFileName().toString()), size);
            cache.put(file.toString(), new Cached(mtime, size, parsed));
            return parsed;
        } catch (IOException e) {
            log.warn("读转录失败：{}", file, e);
            return null;
        }
    }

    /**
     * 取某个 image 块的原始 base64。
     *
     * <p>{@code inner} 给"图嵌在 tool_result 里"那种情况用（例如 {@code list_memes} 一次返回好几张），
     * 传 -1 表示图直接在记录的 content 数组里。
     */
    public byte[] imageBytes(String key, String sessionId, int recordIndex, int blockIndex, int inner) {
        JsonNode block = imageBlock(key, sessionId, recordIndex, blockIndex, inner);
        if (block == null) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(block.path("source").path("data").asText(""));
        } catch (IllegalArgumentException e) {
            log.warn("图片 base64 解不开：key={} record={} block={} inner={}", key, recordIndex, blockIndex, inner);
            return null;
        }
    }

    /** 图片块的 MIME 类型；取不到时按 jpeg 兜底。 */
    public String imageMediaType(String key, String sessionId, int recordIndex, int blockIndex, int inner) {
        JsonNode block = imageBlock(key, sessionId, recordIndex, blockIndex, inner);
        String type = block == null ? "" : block.path("source").path("media_type").asText("");
        return type.isBlank() ? "image/jpeg" : type;
    }

    /**
     * 在一条记录的 content 数组里定位 image 块。
     *
     * <p>{@code inner >= 0} 表示图嵌在 {@code tool_result.content} 里——工具一次返回好几张图时就是
     * 这样，只在外层找的话这类图一张都取不到。
     */
    public static JsonNode imageBlockIn(JsonNode content, int blockIndex, int inner) {
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

    /** 找一个会话下的全部转录文件。 */
    static List<Path> findTranscripts(Path projectsDir) {
        if (!Files.isDirectory(projectsDir)) {
            return List.of();
        }
        List<Path> out = new ArrayList<>();
        try (Stream<Path> slugs = Files.list(projectsDir)) {
            for (Path slug : slugs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> files = Files.list(slug)) {
                    out.addAll(files.filter(ClaudeTranscript::isTranscript).toList());
                }
            }
        } catch (IOException e) {
            log.warn("找转录失败：{}", projectsDir, e);
            return List.of();
        }
        return out;
    }

    private JsonNode imageBlock(String key, String sessionId, int recordIndex, int blockIndex, int inner) {
        Transcript transcript = load(key, sessionId);
        if (transcript == null || recordIndex < 0 || recordIndex >= transcript.entries().size()) {
            return null;
        }
        JsonNode content = transcript.entries().get(recordIndex).json().path("message").path("content");
        return imageBlockIn(content, blockIndex, inner);
    }

    private Path resolveFile(String key, String sessionId) {
        List<Path> all = findTranscripts(workspace.projectsDirOf(key));
        if (all.isEmpty()) {
            return null;
        }
        if (sessionId == null || sessionId.isBlank()) {
            return all.stream().max(Comparator.comparingLong(ClaudeTranscript::mtimeOf)).orElse(null);
        }
        return all.stream()
                .filter(p -> stripExt(p.getFileName().toString()).equals(sessionId))
                .findFirst()
                .orElse(null);
    }

    private Transcript parse(Path file, String sessionId, long size) throws IOException {
        List<ClaudeTranscriptEntry> entries = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int index = 0;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            try {
                JsonNode node = mapper.readTree(line);
                entries.add(new ClaudeTranscriptEntry(index++, node.path("type").asText(""), node));
            } catch (Exception e) {
                // 坏行跳过，不让一行坏数据毁掉整个页面
                log.warn("转录里有解析不了的行，已跳过：{}", file);
            }
        }
        return new Transcript(sessionId, size, lines.size(), entries);
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

    private record Cached(long mtime, long size, Transcript transcript) {
    }

    public record SessionFile(String sessionId, long size, long modifiedAt) {
    }

    public record Transcript(String sessionId, long fileSize, int lineCount,
                             List<ClaudeTranscriptEntry> entries) {
    }
}
