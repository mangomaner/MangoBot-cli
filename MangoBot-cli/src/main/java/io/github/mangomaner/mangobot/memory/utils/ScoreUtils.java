package io.github.mangomaner.mangobot.memory.utils;

import java.time.Duration;
import java.time.LocalDateTime;

public class ScoreUtils {

    /**
     * 计算时间近因性得分 (指数衰减)
     * 24小时内保持高分，之后逐渐衰减
     */
    public static float calculateRecencyScore(LocalDateTime memoryTime) {
        if (memoryTime == null) return 0.5f;
        
        LocalDateTime now = LocalDateTime.now();
        double ageHours = Duration.between(memoryTime, now).toMinutes() / 60.0;
        
        // 衰减系数
        double decayFactor = 0.1;
        double recencyScore = Math.exp(-decayFactor * ageHours / 24.0);
        
        return (float) Math.max(0.1, recencyScore);
    }

    /**
     * 工作记忆评分
     * (相似度 × 时间衰减) × (0.8 + 重要性 × 0.4)
     */
    public static float calculateWorkingScore(float similarity, float recency, float importance) {
        return (similarity * recency) * (0.8f + importance * 0.4f);
    }

    /**
     * 情景记忆评分
     * (向量相似度 × 0.8 + 时间近因性 × 0.2) × (0.8 + 重要性 × 0.4)
     */
    public static float calculateEpisodicScore(float vectorSimilarity, float recency, float importance) {
        return (vectorSimilarity * 0.8f + recency * 0.2f) * (0.8f + importance * 0.4f);
    }

    /**
     * 语义记忆评分
     * (向量相似度 × 0.7 + 图相似度 × 0.3) × (0.8 + 重要性 × 0.4)
     */
    public static float calculateSemanticScore(float vectorSimilarity, float graphSimilarity, float importance) {
        return (vectorSimilarity * 0.7f + graphSimilarity * 0.3f) * (0.8f + importance * 0.4f);
    }

    /**
     * 感知记忆评分
     * (向量相似度 × 0.8 + 时间近因性 × 0.2) × (0.8 + 重要性 × 0.4)
     */
    public static float calculatePerceptualScore(float vectorSimilarity, float recency, float importance) {
        return (vectorSimilarity * 0.8f + recency * 0.2f) * (0.8f + importance * 0.4f);
    }
}
