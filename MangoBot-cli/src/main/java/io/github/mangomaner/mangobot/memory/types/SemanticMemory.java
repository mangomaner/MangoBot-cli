package io.github.mangomaner.mangobot.memory.types;

import io.github.mangomaner.mangobot.memory.base.BaseMemory;
import io.github.mangomaner.mangobot.memory.core.MemoryConfig;
import io.github.mangomaner.mangobot.memory.core.MemoryItem;
import io.github.mangomaner.mangobot.memory.storage.LuceneStore;
import io.github.mangomaner.mangobot.memory.storage.Neo4jGraphStore;
import io.github.mangomaner.mangobot.memory.utils.ScoreUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 语义记忆 (SemanticMemory)
 * 特点：抽象知识，图谱关系
 */
@Slf4j
@Component
public class SemanticMemory extends BaseMemory {

    private final LuceneStore luceneStore;
    private final Neo4jGraphStore graphStore;

    public SemanticMemory(MemoryConfig config, LuceneStore luceneStore, Neo4jGraphStore graphStore) {
        super(config);
        this.luceneStore = luceneStore;
        this.graphStore = graphStore;
    }

    @Override
    public String add(MemoryItem memoryItem) {
        if (memoryItem.getId() == null) memoryItem.setId(generateId());
        memoryItem.setMemoryType(getMemoryType());
        
        try {
            luceneStore.addMemory(memoryItem);
            
            // 尝试从元数据中提取概念并存入图谱
            if (memoryItem.getMetadata() != null && memoryItem.getMetadata().containsKey("concepts")) {
                Object conceptsObj = memoryItem.getMetadata().get("concepts");
                if (conceptsObj instanceof List) {
                    List<?> concepts = (List<?>) conceptsObj;
                    for (Object c : concepts) {
                        graphStore.addConcept(c.toString(), null);
                        // 这里可以添加更复杂的关系构建逻辑
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to add semantic memory", e);
            throw new RuntimeException(e);
        }
        return memoryItem.getId();
    }

    @Override
    public List<MemoryItem> retrieve(String query, int limit) {
        return retrieve(query, limit, 0.0f);
    }

    @Override
    public List<MemoryItem> retrieve(String query, int limit, float minImportance) {
        return retrieve(null, query, limit, minImportance);
    }
    
    public List<MemoryItem> retrieve(float[] vector, String text, int limit, float minImportance) {
        // 1. 获取向量/文本检索候选
        List<LuceneStore.SearchResult> candidates = luceneStore.search(vector, text, limit * 2, getMemoryType(), 0.0f);
        
        // 2. 结合图谱评分
        // 这里的难点是：如何计算 Query 与 Candidate 之间的 Graph Similarity？
        // 假设 Query 中提取了关键实体 (Entity Linking)，然后在图谱中计算与 Candidate 关联实体的距离。
        // 简化实现：假设 text 本身包含概念，或者我们暂时忽略图相似度（设为0），或者进行简单的名称匹配。
        // 为了演示，我们假设 query text 就是一个 concept name。
        
        return candidates.stream()
                .map(result -> {
                    MemoryItem item = result.getItem();
                    float similarity = result.getScore();
                    
                    // 计算图相似度
                    float graphSim = 0.0f;
                    // 假设 item content 中包含概念名，query 也是概念名
                    // graphSim = graphStore.calculateGraphSimilarity(text, item.getContent());
                    // 实际应用中需要实体链接
                    
                    // 评分公式：(向量相似度 × 0.7 + 图相似度 × 0.3) × (0.8 + 重要性 × 0.4)
                    float finalScore = ScoreUtils.calculateSemanticScore(similarity, graphSim, item.getImportance());
                    
                    return new ScoredItem(item, finalScore);
                })
                .filter(si -> si.item.getImportance() >= minImportance)
                .sorted(Comparator.comparingDouble(ScoredItem::getScore).reversed())
                .limit(limit)
                .map(ScoredItem::getItem)
                .collect(Collectors.toList());
    }

    @Override
    public boolean update(String memoryId, String content, Float importance, Map<String, Object> metadata) {
        return false;
    }

    @Override
    public boolean remove(String memoryId) {
        try {
            luceneStore.removeMemory(memoryId);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean hasMemory(String memoryId) {
        return false;
    }

    @Override
    public void clear() {
        graphStore.clear();
    }

    @Override
    public Map<String, Object> getStats() {
        return new HashMap<>();
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ScoredItem {
        private MemoryItem item;
        private double score;
    }
}
