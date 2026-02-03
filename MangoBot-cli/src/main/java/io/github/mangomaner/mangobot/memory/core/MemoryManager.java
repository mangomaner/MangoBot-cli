package io.github.mangomaner.mangobot.memory.core;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.github.mangomaner.mangobot.config.AiConfig;
import io.github.mangomaner.mangobot.memory.types.EpisodicMemory;
import io.github.mangomaner.mangobot.memory.types.PerceptualMemory;
import io.github.mangomaner.mangobot.memory.types.SemanticMemory;
import io.github.mangomaner.mangobot.memory.types.WorkingMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆管理器 (MemoryManager)
 * 统一调度和协调各记忆类型
 */
@Slf4j
@Component
public class MemoryManager {

    private final WorkingMemory workingMemory;
    private final EpisodicMemory episodicMemory;
    private final SemanticMemory semanticMemory;
    private final PerceptualMemory perceptualMemory;
    private final MemoryConfig config;

    public MemoryManager(WorkingMemory workingMemory,
                         EpisodicMemory episodicMemory,
                         SemanticMemory semanticMemory,
                         PerceptualMemory perceptualMemory,
                         MemoryConfig config) {
        this.workingMemory = workingMemory;
        this.episodicMemory = episodicMemory;
        this.semanticMemory = semanticMemory;
        this.perceptualMemory = perceptualMemory;
        this.config = config;
    }

    /**
     * 添加记忆
     * @param content 内容
     * @param memoryType 类型 (可选，若为空则自动分类)
     */
    public String addMemory(String content, String memoryType, String userId, Float importance, Map<String, Object> metadata) {
        // 1. 自动分类
        if (memoryType == null || memoryType.isEmpty()) {
            memoryType = classifyMemoryType(content, metadata);
        }

        // 2. 生成向量 (Embedding)
        float[] vector = generateEmbedding(content);
        
        // 3. 构建记忆项
        MemoryItem item = MemoryItem.builder()
                .content(content)
                .memoryType(memoryType)
                .userId(userId != null ? userId : "default_user")
                .timestamp(LocalDateTime.now())
                .importance(importance != null ? importance : 0.5f)
                .metadata(metadata != null ? metadata : new HashMap<>())
                .embedding(vector)
                .build();

        // 4. 分发到对应存储
        switch (memoryType.toLowerCase()) {
            case "working":
                return workingMemory.add(item);
            case "episodic":
                return episodicMemory.add(item);
            case "semantic":
                return semanticMemory.add(item);
            case "perceptual":
                return perceptualMemory.add(item);
            default:
                throw new IllegalArgumentException("Unknown memory type: " + memoryType);
        }
    }

    /**
     * 检索记忆
     */
    public List<MemoryItem> retrieveMemories(String query, List<String> memoryTypes, int limit, float minImportance) {
        if (memoryTypes == null || memoryTypes.isEmpty()) {
            memoryTypes = Arrays.asList("working", "episodic", "semantic");
        }
        
        // 生成查询向量
        float[] vector = generateEmbedding(query);
        
        List<MemoryItem> allResults = new ArrayList<>();
        int perTypeLimit = Math.max(1, limit); // 稍微多查一点

        // 并行或串行检索
        if (memoryTypes.contains("working")) {
            allResults.addAll(workingMemory.retrieve(vector, query, perTypeLimit, minImportance));
        }
        if (memoryTypes.contains("episodic")) {
            allResults.addAll(episodicMemory.retrieve(vector, query, perTypeLimit, minImportance));
        }
        if (memoryTypes.contains("semantic")) {
            allResults.addAll(semanticMemory.retrieve(vector, query, perTypeLimit, minImportance));
        }
        if (memoryTypes.contains("perceptual")) {
            allResults.addAll(perceptualMemory.retrieve(vector, query, perTypeLimit, minImportance));
        }

        // 统一排序并截断
        // 注意：不同类型的分数可能不在同一量级，这里假设 ScoreUtils 已经归一化处理
        return allResults.stream()
                .sorted((a, b) -> Float.compare(b.getImportance(), a.getImportance())) // 简单按重要性排，或者应该按 score 排
                // 这里有个问题：BaseMemory.retrieve 返回的是 MemoryItem，丢失了 score。
                // 在 Controller 或上层应用中，通常看重结果本身。
                // 如果需要严格按 score 混排，retrieve 应该返回 ScoredMemoryItem。
                // 暂时按 importance 排序作为兜底。
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 记忆整合 (Short -> Long)
     */
    public int consolidateMemories() {
        // 简单实现：将高重要性的工作记忆移动到情景记忆
        List<MemoryItem> workingItems = workingMemory.retrieve(null, "", 100, config.getImportanceThreshold());
        int count = 0;
        for (MemoryItem item : workingItems) {
            if (item.getImportance() > 0.7f) {
                // 移除旧的
                workingMemory.remove(item.getId());
                // 修改类型并添加新的
                item.setMemoryType("episodic");
                item.setId(null); // regenerate ID
                episodicMemory.add(item);
                count++;
            }
        }
        log.info("Consolidated {} memories from Working to Episodic", count);
        return count;
    }

    public void clearAll() {
        workingMemory.clear();
        episodicMemory.clear();
        semanticMemory.clear();
        perceptualMemory.clear();
        log.info("All memories cleared");
    }

    private String classifyMemoryType(String content, Map<String, Object> metadata) {
        if (metadata != null && metadata.containsKey("type")) {
            return (String) metadata.get("type");
        }
        
        // 简单规则
        if (content.contains("定义") || content.contains("概念") || content.contains("是")) {
            return "semantic";
        }
        if (content.contains("昨天") || content.contains("记得") || content.contains("发生")) {
            return "episodic";
        }
        
        return "working";
    }

    private float[] generateEmbedding(String text) {
        EmbeddingModel model = AiConfig.getEmbeddingModel();
        if (model != null && text != null && !text.isEmpty()) {
            try {
                Embedding embedding = model.embed(TextSegment.from(text)).content();
                return embedding.vector();
            } catch (Exception e) {
                log.warn("Embedding generation failed", e);
            }
        }
        return null;
    }
}
