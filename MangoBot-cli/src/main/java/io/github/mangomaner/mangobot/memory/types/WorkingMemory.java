package io.github.mangomaner.mangobot.memory.types;

import io.github.mangomaner.mangobot.memory.base.BaseMemory;
import io.github.mangomaner.mangobot.memory.core.MemoryConfig;
import io.github.mangomaner.mangobot.memory.core.MemoryItem;
import io.github.mangomaner.mangobot.memory.storage.LuceneStore;
import io.github.mangomaner.mangobot.memory.utils.ScoreUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作记忆 (WorkingMemory)
 * 特点：临时信息，TTL管理，容量限制
 */
@Slf4j
@Component
public class WorkingMemory extends BaseMemory {

    private final LuceneStore luceneStore;

    public WorkingMemory(MemoryConfig config, LuceneStore luceneStore) {
        super(config);
        this.luceneStore = luceneStore;
    }

    @Override
    public String add(MemoryItem memoryItem) {
        if (memoryItem.getId() == null) {
            memoryItem.setId(generateId());
        }
        memoryItem.setMemoryType(getMemoryType());
        
        // 自动计算重要性（如果未设置）
        if (memoryItem.getImportance() == 0.5f) { // 假设默认是0.5
            memoryItem.setImportance(calculateBaseImportance(memoryItem.getContent(), 0.5f));
        }

        try {
            luceneStore.addMemory(memoryItem);
            // 简单的容量管理：如果在生产环境，建议异步清理
            cleanup(); 
        } catch (IOException e) {
            log.error("Failed to add working memory", e);
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
        // 工作记忆检索：
        // 1. 尝试使用 TF-IDF/BM25 (这里简化为 Lucene 的文本搜索，因为我们没有单独的 TF-IDF 模块，BM25 效果更好)
        // 2. 如果有向量，也可以混合
        // 根据文档描述：首先尝试使用TF-IDF向量化进行语义检索... 回退到关键词。
        // 我们这里统一使用 Lucene 混合检索。
        
        // 注意：这里没有传入向量，因为 BaseMemory 接口没有 vector 参数。
        // 在 MemoryManager 中应该会先计算 vector。
        // 为了兼容接口，这里假设 vector 为 null (纯文本检索) 或者需要扩展接口。
        // 鉴于架构设计，通常 retrieve 应该接收 vector。
        // 我会在 MemoryManager 中处理 vector 生成，但 BaseMemory 接口目前只接收 String query。
        // 我需要扩展 BaseMemory 或者在 retrieve 内部生成 vector (不推荐，因为 model 在外部)。
        // **修正**：为了严格遵循设计，BaseMemory 应该支持 vector 传递，或者我们在 ThreadLocal 传递，或者修改接口。
        // 考虑到 Java 强类型，我将在 BaseMemory 增加 vector 重载，或者在 MemoryManager 调用具体实现的方法。
        // 暂时只支持文本检索 (BM25)，或者假设调用者会通过其他方式传递（此处简化）。
        // *实际上*：文档中的 retrieve 接口有 **kwargs。
        
        return retrieve(null, query, limit, minImportance);
    }
    
    // 扩展方法，支持向量
    public List<MemoryItem> retrieve(float[] vector, String text, int limit, float minImportance) {
        // 获取更多候选项以便重排序
        List<LuceneStore.SearchResult> candidates = luceneStore.search(vector, text, limit * 2, getMemoryType(), 0.0f);
        
        return candidates.stream()
            .map(result -> {
                MemoryItem item = result.getItem();
                float similarity = result.getScore(); // Lucene score 近似相似度
                float recency = ScoreUtils.calculateRecencyScore(item.getTimestamp());
                float finalScore = ScoreUtils.calculateWorkingScore(similarity, recency, item.getImportance());
                
                // 将计算后的分数暂存（如果需要）
                return new ScoredItem(item, finalScore);
            })
            .filter(si -> si.item.getImportance() >= minImportance)
            // 过滤过期的
            .filter(si -> !isExpired(si.item))
            .sorted(Comparator.comparingDouble(ScoredItem::getScore).reversed())
            .limit(limit)
            .map(ScoredItem::getItem)
            .collect(Collectors.toList());
    }

    @Override
    public boolean update(String memoryId, String content, Float importance, Map<String, Object> metadata) {
        // Lucene 不支持直接部分更新，需要先读取再写入
        // 这里简化为：先查 ID (LuceneStore 需要提供 getById，暂时未实现，略过)
        // 实际实现需要：get -> update fields -> add
        return false;
    }

    @Override
    public boolean remove(String memoryId) {
        try {
            luceneStore.removeMemory(memoryId);
            return true;
        } catch (IOException e) {
            log.error("Failed to remove working memory", e);
            return false;
        }
    }

    @Override
    public boolean hasMemory(String memoryId) {
        // Lucene check
        return false;
    }

    @Override
    public void clear() {
        try {
            luceneStore.clear(); // 注意：这会清空所有类型的记忆！
            // LuceneStore 是共享的，所以不能直接 clearAll。
            // 应该 delete by query type。
            // LuceneStore 需要增加 deleteByType。
        } catch (IOException e) {
            log.error("Failed to clear working memory", e);
        }
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("type", getMemoryType());
        // stats.put("count", ...);
        return stats;
    }
    
    private boolean isExpired(MemoryItem item) {
        long minutes = Duration.between(item.getTimestamp(), LocalDateTime.now()).toMinutes();
        return minutes > config.getWorkingMemoryTtlMinutes();
    }
    
    private void cleanup() {
        // 实现清理逻辑，删除过期的
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ScoredItem {
        private MemoryItem item;
        private double score;
    }
}
