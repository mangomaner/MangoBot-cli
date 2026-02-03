package io.github.mangomaner.mangobot.memory.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 记忆项数据结构 (标准化记忆项)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryItem {
    private String id;
    private String content;
    private String memoryType; // working, episodic, semantic, perceptual
    private String userId;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    @Builder.Default
    private float importance = 0.5f;
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    // 向量数据，不一定每次都加载，但在存储时需要
    private float[] embedding;
}
