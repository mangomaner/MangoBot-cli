package io.github.mangomaner.mangobot.memory.types;

import io.github.mangomaner.mangobot.memory.base.BaseMemory;
import io.github.mangomaner.mangobot.memory.core.MemoryConfig;
import io.github.mangomaner.mangobot.memory.core.MemoryItem;
import io.github.mangomaner.mangobot.memory.storage.LuceneStore;
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
 * 情景记忆 (EpisodicMemory)
 * 特点：具体事件，时间序列，强调时间近因性
 */
@Slf4j
@Component
public class EpisodicMemory extends BaseMemory {

    private final LuceneStore luceneStore;

    public EpisodicMemory(MemoryConfig config, LuceneStore luceneStore) {
        super(config);
        this.luceneStore = luceneStore;
    }

    @Override
    public String add(MemoryItem memoryItem) {
        if (memoryItem.getId() == null) memoryItem.setId(generateId());
        memoryItem.setMemoryType(getMemoryType());
        if (memoryItem.getImportance() == 0.5f) {
            memoryItem.setImportance(calculateBaseImportance(memoryItem.getContent(), 0.5f));
        }

        try {
            luceneStore.addMemory(memoryItem);
        } catch (IOException e) {
            log.error("Failed to add episodic memory", e);
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
        List<LuceneStore.SearchResult> candidates = luceneStore.search(vector, text, limit * 2, getMemoryType(), 0.0f);

        return candidates.stream()
                .map(result -> {
                    MemoryItem item = result.getItem();
                    float similarity = result.getScore();
                    float recency = ScoreUtils.calculateRecencyScore(item.getTimestamp());
                    
                    // 评分公式：(向量相似度 × 0.8 + 时间近因性 × 0.2) × (0.8 + 重要性 × 0.4)
                    float finalScore = ScoreUtils.calculateEpisodicScore(similarity, recency, item.getImportance());
                    
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
        // Not implemented specifically for type yet
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
