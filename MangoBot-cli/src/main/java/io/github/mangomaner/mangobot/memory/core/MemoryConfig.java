package io.github.mangomaner.mangobot.memory.core;

import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 配置管理（系统参数设置）
 */
@Data
@Component
public class MemoryConfig {

    // 存储路径配置
    private String storagePath = "data/memory_data";
    private String lucenePath = "lucene_index";
    private String neo4jPath = "neo4j_data";

    // 基础统计配置
    private int maxCapacity = 100;
    private float importanceThreshold = 0.1f;
    private float decayFactor = 0.95f;

    // 工作记忆配置
    private int workingMemoryCapacity = 10;
    private int workingMemoryTokens = 2000;
    private int workingMemoryTtlMinutes = 120;
    
    // 向量维度 (默认，会被实际模型覆盖)
    private int dimension = 1536;

    public Path getStoragePath() {
        return FileUtils.resolvePath(storagePath);
    }
    
    public Path getLucenePath() {
        return getStoragePath().resolve(lucenePath);
    }
    
    public Path getNeo4jPath() {
        return getStoragePath().resolve(neo4jPath);
    }
}
