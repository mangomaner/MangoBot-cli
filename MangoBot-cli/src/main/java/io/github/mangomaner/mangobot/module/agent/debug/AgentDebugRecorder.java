package io.github.mangomaner.mangobot.module.agent.debug;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.module.agent.model.vo.TokenUsageVO;
import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 调试「记录」：按会话持久化记录开关，并把每次完整对话（一条用户输入 + AI 整体输出）追加到文件。
 *
 * <p>文件布局（相对应用根目录）：
 * <ul>
 *   <li>{@code data/debug/settings.json}：{@code {"<sessionId>": true, ...}}，记录开关，重启不丢；</li>
 *   <li>{@code data/debug/<sessionId>.jsonl}：每次完整对话一行 JSON
 *       {@code {"time": "...", "input": {"messages": [...], "tools": [...]}, "output": "<AI整体输出>", "usage": {...}, "calls": [...]}}；</li>
 *   <li>{@code data/debug/<sessionId>.summary.jsonl}：轻量索引，每次记录一行
 *       {@code {"offset": <大文件行起始字节>, "time": "...", "usage": {...}, "triggerText": "<触发用户消息>"}}。</li>
 * </ul>
 *
 * <p>记录粒度是「一次完整对话」而非「每次模型调用」：同一提问即使内部触发多轮工具调用
 * （多次 LLM 请求），也只落一行，token 用量为该轮所有调用之和，逐次调用明细放在 calls 中。
 * {@code input} 保留旧格式的完整上下文（消息列表 + 工具清单），即该轮最后一次模型调用发送给模型的内容。
 *
 * <p>性能：列表/计数只读轻量 summary 文件（O(N) 小字段）；某条记录详情按 summary 中的字节偏移
 * 直接 seek 到大文件该行，避免把整个大文件读入内存。大文件本身因每条记录都存完整 input 快照而随轮数增长
 * （历史记录越靠后越大），因此 summary 索引是读取快速的关键。
 */
@Slf4j
@Component
public class AgentDebugRecorder {

    private static final String DEBUG_DIR = "data/debug";
    private static final String SETTINGS_FILE = "settings.json";
    private static final String SUMMARY_SUFFIX = ".summary.jsonl";
    private static final TypeReference<Map<String, Boolean>> SETTINGS_TYPE = new TypeReference<>() {};

    private final Object settingsLock = new Object();
    /** 串行化所有写操作（追加/删除/重建索引），保证字节偏移计算不与其他写入竞争 */
    private final Object appendLock = new Object();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** sessionId -> 是否开启记录（仅保留开启项） */
    private final ConcurrentHashMap<Integer, Boolean> recording = new ConcurrentHashMap<>();
    private volatile boolean settingsLoaded = false;

    private Path debugDir() {
        return FileUtils.resolvePath(DEBUG_DIR);
    }

    private Path settingsPath() {
        return debugDir().resolve(SETTINGS_FILE);
    }

    private Path recordFile(Integer sessionId) {
        return debugDir().resolve(sessionId + ".jsonl");
    }

    private Path summaryFile(Integer sessionId) {
        return debugDir().resolve(sessionId + SUMMARY_SUFFIX);
    }

    private void ensureLoaded() {
        if (settingsLoaded) {
            return;
        }
        synchronized (settingsLock) {
            if (settingsLoaded) {
                return;
            }
            Path path = settingsPath();
            if (Files.exists(path)) {
                try {
                    Map<String, Boolean> map = objectMapper.readValue(Files.readString(path, StandardCharsets.UTF_8), SETTINGS_TYPE);
                    map.forEach((key, value) -> {
                        if (Boolean.TRUE.equals(value)) {
                            recording.put(Integer.valueOf(key), true);
                        }
                    });
                } catch (Exception e) {
                    log.warn("Failed to load debug settings", e);
                }
            }
            settingsLoaded = true;
        }
    }

    public boolean isRecording(Integer sessionId) {
        if (sessionId == null) {
            return false;
        }
        ensureLoaded();
        return Boolean.TRUE.equals(recording.get(sessionId));
    }

    public void setRecording(Integer sessionId, boolean enabled) {
        if (sessionId == null) {
            return;
        }
        ensureLoaded();
        synchronized (settingsLock) {
            if (enabled) {
                recording.put(sessionId, true);
            } else {
                recording.remove(sessionId);
            }
            persistSettingsLocked();
        }
    }

    private void persistSettingsLocked() {
        try {
            Map<String, Boolean> out = new LinkedHashMap<>();
            recording.forEach((key, value) -> out.put(String.valueOf(key), value));
            FileUtils.createDirectory(debugDir());
            FileUtils.writeString(settingsPath(), objectMapper.writeValueAsString(out));
        } catch (Exception e) {
            log.warn("Failed to persist debug settings", e);
        }
    }

    /**
     * 追加一次完整对话记录（仅在开启记录时落盘）。同时追加一条轻量 summary 索引。
     *
     * @param sessionId 会话 ID
     * @param inputJson 该轮最后一次模型调用发送给模型的完整上下文（JSON 字符串：messages + tools）
     * @param output    模型整体输出（含思考/工具调用等原始内容）
     * @param usage     本轮所有模型调用用量之和，可为 null
     * @param calls     本轮每次模型调用的用量明细，可为 null/空
     */
    public void appendTurn(Integer sessionId, String inputJson, String output,
                           TokenUsageVO usage, List<TokenUsageVO> calls) {
        if (!isRecording(sessionId)) {
            return;
        }
        try {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("time", Instant.now().toString());
            record.put("input", inputJson != null ? objectMapper.readValue(inputJson, Map.class) : null);
            record.put("output", output);
            if (usage != null) {
                record.put("usage", usage);
            }
            if (calls != null && !calls.isEmpty()) {
                record.put("calls", calls);
            }
            String recordJson = objectMapper.writeValueAsString(record);

            String triggerText = extractTriggerText(record.get("input"));

            FileUtils.createDirectory(debugDir());
            synchronized (appendLock) {
                Path file = recordFile(sessionId);
                long offset = Files.exists(file) ? Files.size(file) : 0L;
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("offset", offset);
                summary.put("time", record.get("time"));
                if (usage != null) {
                    summary.put("usage", usage);
                }
                if (triggerText != null) {
                    summary.put("triggerText", triggerText);
                }
                FileUtils.appendString(file, recordJson + System.lineSeparator());
                FileUtils.appendString(summaryFile(sessionId),
                        objectMapper.writeValueAsString(summary) + System.lineSeparator());
            }
        } catch (Exception e) {
            log.warn("Failed to append debug record for session: {}", sessionId, e);
        }
    }

    /** 已记录的模型调用次数（summary 索引行数） */
    public int recordCount(Integer sessionId) {
        if (sessionId == null) {
            return 0;
        }
        ensureSummaryIndex(sessionId);
        Path file = summaryFile(sessionId);
        if (!Files.exists(file)) {
            return 0;
        }
        int count = 0;
        for (String line : readLinesLenient(file)) {
            if (!line.isBlank()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 删除第 index 条可解析的记录（0 基下标，与 summary 顺序一致）。
     * 通过重写文件跳过该行实现；随后重建 summary 索引以保证字节偏移正确。
     */
    public void removeRecord(Integer sessionId, int index) {
        if (sessionId == null || index < 0) {
            return;
        }
        synchronized (appendLock) {
            Path file = recordFile(sessionId);
            if (!Files.exists(file)) {
                return;
            }
            try {
                List<String> lines = readLinesLenient(file);
                int validIdx = 0;
                boolean removed = false;
                List<String> out = new ArrayList<>(lines.size());
                for (String line : lines) {
                    boolean parseable = parseable(line);
                    if (parseable && !removed && validIdx == index) {
                        removed = true;
                        continue;
                    }
                    if (parseable) {
                        validIdx++;
                    }
                    out.add(line);
                }
                if (removed) {
                    Files.write(file, out, StandardCharsets.UTF_8);
                    rebuildSummaryIndex(sessionId);
                }
            } catch (Exception e) {
                log.warn("Failed to remove debug record #{} for session: {}", index, sessionId, e);
            }
        }
    }

    /** 清空该会话的所有已记录内容（删除大文件与 summary 索引），记录开关保持不变。 */
    public void clearRecords(Integer sessionId) {
        if (sessionId == null) {
            return;
        }
        synchronized (appendLock) {
            try {
                Files.deleteIfExists(recordFile(sessionId));
                Files.deleteIfExists(summaryFile(sessionId));
            } catch (Exception e) {
                log.warn("Failed to clear debug records for session: {}", sessionId, e);
            }
        }
    }

    /** 该会话已记录调用的轻量摘要列表（index/time/usage/triggerText），供调试面板列表渲染 */
    public List<Map<String, Object>> listRecordSummaries(Integer sessionId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (sessionId == null) {
            return result;
        }
        ensureSummaryIndex(sessionId);
        Path file = summaryFile(sessionId);
        if (!Files.exists(file)) {
            return result;
        }
        int idx = 0;
        for (String line : readLinesLenient(file)) {
            if (line.isBlank()) {
                continue;
            }
            try {
                Map<String, Object> sum = objectMapper.readValue(line, Map.class);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("index", idx);
                Object time = sum.get("time");
                if (time != null) {
                    out.put("time", time);
                }
                Object usage = sum.get("usage");
                if (usage != null) {
                    out.put("usage", usage);
                }
                Object trigger = sum.get("triggerText");
                if (trigger != null) {
                    out.put("triggerText", trigger);
                }
                result.add(out);
                idx++;
            } catch (Exception e) {
                log.warn("Failed to parse debug summary line: {}", e.getMessage());
            }
        }
        return result;
    }

    /** 取第 index 条可解析记录的完整内容（0 基下标，与 summary 顺序一致），无则返回 null；offset 直达，不读整个文件 */
    public Map<String, Object> getRecord(Integer sessionId, int index) {
        if (sessionId == null || index < 0) {
            return null;
        }
        ensureSummaryIndex(sessionId);
        Long offset = summaryOffset(sessionId, index);
        if (offset == null) {
            return null;
        }
        Path file = recordFile(sessionId);
        if (!Files.exists(file)) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file.toFile())) {
            if (fis.skip(offset) != offset) {
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null || line.isBlank()) {
                return null;
            }
            return objectMapper.readValue(line, Map.class);
        } catch (Exception e) {
            log.warn("Failed to read debug record #{} for session: {}", index, sessionId, e);
            return null;
        }
    }

    /** 从 summary 文件读取第 index 条的字节偏移，无则返回 null */
    private Long summaryOffset(Integer sessionId, int index) {
        Path file = summaryFile(sessionId);
        if (!Files.exists(file)) {
            return null;
        }
        int idx = 0;
        for (String line : readLinesLenient(file)) {
            if (line.isBlank()) {
                continue;
            }
            try {
                Map<String, Object> sum = objectMapper.readValue(line, Map.class);
                if (idx == index) {
                    Object offset = sum.get("offset");
                    return offset instanceof Number ? ((Number) offset).longValue() : null;
                }
                idx++;
            } catch (Exception e) {
                log.warn("Failed to parse debug summary line: {}", e.getMessage());
            }
        }
        return null;
    }

    /** 若 summary 索引不存在，从大文件重建（兼容历史数据） */
    private void ensureSummaryIndex(Integer sessionId) {
        if (Files.exists(summaryFile(sessionId))) {
            return;
        }
        synchronized (appendLock) {
            if (Files.exists(summaryFile(sessionId))) {
                return;
            }
            rebuildSummaryIndex(sessionId);
        }
    }

    /**
     * 扫描大文件，重建该会话的 summary 索引（含每行字节偏移）。调用方需持有 appendLock。
     */
    private void rebuildSummaryIndex(Integer sessionId) {
        Path file = recordFile(sessionId);
        if (!Files.exists(file)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        long offset = 0;
        for (String line : readLinesLenient(file)) {
            long lineStart = offset;
            offset += line.getBytes(StandardCharsets.UTF_8).length + 1;
            if (line.isBlank()) {
                continue;
            }
            try {
                Map<String, Object> record = objectMapper.readValue(line, Map.class);
                Map<String, Object> sum = new LinkedHashMap<>();
                sum.put("offset", lineStart);
                Object time = record.get("time");
                if (time != null) {
                    sum.put("time", time);
                }
                Object usage = record.get("usage");
                if (usage != null) {
                    sum.put("usage", usage);
                }
                String trigger = extractTriggerText(record.get("input"));
                if (trigger != null) {
                    sum.put("triggerText", trigger);
                }
                sb.append(objectMapper.writeValueAsString(sum)).append(System.lineSeparator());
            } catch (Exception e) {
                log.warn("Failed to parse debug record line: {}", e.getMessage());
            }
        }
        FileUtils.createDirectory(debugDir());
        FileUtils.writeString(summaryFile(sessionId), sb.toString());
    }

    private boolean parseable(String line) {
        try {
            objectMapper.readValue(line, Map.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 宽松读取 jsonl 全部分行：按字节解码为 UTF-8（非法字节替换为替换符，不抛异常），再按换行拆分。
     * 严格解码读历史/损坏文件会抛 MalformedInputException，导致删除/清空/计数全部失败。
     */
    private List<String> readLinesLenient(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            return Arrays.asList(new String(bytes, StandardCharsets.UTF_8).split("\n", -1));
        } catch (Exception e) {
            log.warn("Failed to read debug record file: {}", file, e);
            return List.of();
        }
    }

    /** 从记录的 input（messages + tools）中提取「最后一条用户消息」的正文字，用于在聊天中定位记录分隔线 */
    @SuppressWarnings("unchecked")
    private static String extractTriggerText(Object input) {
        if (!(input instanceof Map)) {
            return null;
        }
        Object messages = ((Map<String, Object>) input).get("messages");
        if (!(messages instanceof List)) {
            return null;
        }
        List<?> list = (List<?>) messages;
        for (int i = list.size() - 1; i >= 0; i--) {
            Object m = list.get(i);
            if (!(m instanceof Map)) {
                continue;
            }
            Map<String, Object> msg = (Map<String, Object>) m;
            Object role = msg.get("role");
            if (role == null || !"user".equalsIgnoreCase(String.valueOf(role))) {
                continue;
            }
            String text = extractMessageText(msg.get("content"));
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    /** 从消息 content（字符串或多段 content 块数组/单块对象）中提取全文 */
    @SuppressWarnings("unchecked")
    private static String extractMessageText(Object content) {
        if (content == null) {
            return null;
        }
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object c : (List<?>) content) {
                if (c instanceof String) {
                    sb.append((String) c);
                } else if (c instanceof Map) {
                    Object text = ((Map<String, Object>) c).get("text");
                    if (text != null) {
                        sb.append(String.valueOf(text));
                    }
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
        if (content instanceof Map) {
            Object text = ((Map<String, Object>) content).get("text");
            return text != null ? String.valueOf(text) : null;
        }
        return null;
    }
}
