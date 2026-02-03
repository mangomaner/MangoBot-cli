package io.github.mangomaner.mangobot.memory.base;

import io.github.mangomaner.mangobot.memory.core.MemoryConfig;
import io.github.mangomaner.mangobot.memory.core.MemoryItem;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 记忆基类（通用接口定义）
 */
@Slf4j
public abstract class BaseMemory {

    @Getter
    protected final MemoryConfig config;
    @Getter
    protected final String memoryType;

    protected BaseMemory(MemoryConfig config) {
        this.config = config;
        this.memoryType = this.getClass().getSimpleName().replace("Memory", "").toLowerCase();
    }

    /**
     * 添加记忆项
     * @param memoryItem 记忆项对象
     * @return 记忆ID
     */
    public abstract String add(MemoryItem memoryItem);

    /**
     * 检索相关记忆
     * @param query 查询内容
     * @param limit 返回数量限制
     * @return 相关记忆列表
     */
    public abstract List<MemoryItem> retrieve(String query, int limit);
    
    /**
     * 高级检索（支持更多参数）
     */
    public abstract List<MemoryItem> retrieve(String query, int limit, float minImportance);

    /**
     * 更新记忆
     */
    public abstract boolean update(String memoryId, String content, Float importance, Map<String, Object> metadata);

    /**
     * 删除记忆
     */
    public abstract boolean remove(String memoryId);

    /**
     * 检查记忆是否存在
     */
    public abstract boolean hasMemory(String memoryId);

    /**
     * 清空所有记忆
     */
    public abstract void clear();

    /**
     * 获取记忆统计信息
     */
    public abstract Map<String, Object> getStats();

    protected String generateId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 计算记忆重要性
     */
    protected float calculateBaseImportance(String content, float baseImportance) {
        float importance = baseImportance;

        // 基于内容长度
        if (content != null && content.length() > 100) {
            importance += 0.1f;
        }

        // 基于关键词
        String[] importantKeywords = {"重要", "关键", "必须", "注意", "警告", "错误"};
        if (content != null) {
            for (String keyword : importantKeywords) {
                if (content.contains(keyword)) {
                    importance += 0.2f;
                    break;
                }
            }
        }

        return Math.max(0.0f, Math.min(1.0f, importance));
    }
    
    @Override
    public String toString() {
        return String.format("%s(type=%s)", this.getClass().getSimpleName(), memoryType);
    }
}
