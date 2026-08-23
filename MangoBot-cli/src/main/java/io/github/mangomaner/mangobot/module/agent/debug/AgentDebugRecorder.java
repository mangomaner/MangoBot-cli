package io.github.mangomaner.mangobot.module.agent.debug;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.module.agent.model.vo.TokenUsageVO;
import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
 *       {@code {"time": "...", "input": {"messages": [...], "tools": [...]}, "output": "<AI整体输出>", "usage": {...}, "calls": [...]}}。</li>
 * </ul>
 *
 * <p>记录粒度是「一次完整对话」而非「每次模型调用」：同一提问即使内部触发多轮工具调用
 * （多次 LLM 请求），也只落一行，token 用量为该轮所有调用之和，逐次调用明细放在 calls 中。
 * {@code input} 保留旧格式的完整上下文（消息列表 + 工具清单），即该轮最后一次模型调用发送给模型的内容。
 */
@Slf4j
@Component
public class AgentDebugRecorder {

    private static final String DEBUG_DIR = "data/debug";
    private static final String SETTINGS_FILE = "settings.json";
    private static final TypeReference<Map<String, Boolean>> SETTINGS_TYPE = new TypeReference<>() {};

    private final Object settingsLock = new Object();
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
     * 追加一次完整对话记录（仅在开启记录时落盘）。
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
            FileUtils.createDirectory(debugDir());
            FileUtils.appendString(recordFile(sessionId),
                    objectMapper.writeValueAsString(record) + System.lineSeparator());
        } catch (Exception e) {
            log.warn("Failed to append debug record for session: {}", sessionId, e);
        }
    }

    /** 已记录的模型调用次数（可解析记录条数） */
    public int recordCount(Integer sessionId) {
        if (sessionId == null) {
            return 0;
        }
        Path file = recordFile(sessionId);
        if (!Files.exists(file)) {
            return 0;
        }
        int count = 0;
        for (String line : readLinesLenient(file)) {
            if (parseable(line)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 删除第 index 条可解析的记录（0 基下标，与 listRecords 返回顺序一致）。
     * 通过重写文件跳过该行实现；解析失败的残行保留原位，不影响其余记录下标。
     */
    public void removeRecord(Integer sessionId, int index) {
        if (sessionId == null || index < 0) {
            return;
        }
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
            }
        } catch (Exception e) {
            log.warn("Failed to remove debug record #{} for session: {}", index, sessionId, e);
        }
    }

    /** 清空该会话的所有已记录内容（删除整个 jsonl 文件），记录开关保持不变。 */
    public void clearRecords(Integer sessionId) {
        if (sessionId == null) {
            return;
        }
        try {
            Files.deleteIfExists(recordFile(sessionId));
        } catch (Exception e) {
            log.warn("Failed to clear debug records for session: {}", sessionId, e);
        }
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

    /** 该会话已记录的完整调用列表（按时间先后，跳过解析失败的行） */
    public List<Map<String, Object>> listRecords(Integer sessionId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (sessionId == null) {
            return result;
        }
        Path file = recordFile(sessionId);
        if (!Files.exists(file)) {
            return result;
        }
        for (String line : readLinesLenient(file)) {
            if (line.isBlank()) {
                continue;
            }
            try {
                result.add(objectMapper.readValue(line, Map.class));
            } catch (Exception e) {
                log.warn("Failed to parse debug record line: {}", e.getMessage());
            }
        }
        return result;
    }
}
