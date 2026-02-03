package io.github.mangomaner.mangobot.controller;

import io.github.mangomaner.mangobot.memory.core.MemoryManager;
import io.github.mangomaner.mangobot.memory.core.MemoryItem;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryManager memoryManager;

    public MemoryController(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    @PostMapping("/add")
    public String addMemory(@RequestBody AddMemoryRequest request) {
        return memoryManager.addMemory(
            request.getContent(),
            request.getType(),
            request.getUserId(),
            request.getImportance(),
            request.getMetadata()
        );
    }

    @PostMapping("/search")
    public List<MemoryItem> searchMemory(@RequestBody SearchMemoryRequest request) {
        return memoryManager.retrieveMemories(
            request.getQuery(),
            request.getTypes(),
            request.getLimit() > 0 ? request.getLimit() : 10,
            request.getMinImportance()
        );
    }

    @PostMapping("/consolidate")
    public int consolidateMemories() {
        return memoryManager.consolidateMemories();
    }

    @DeleteMapping("/clear")
    public String clearAll() {
        memoryManager.clearAll();
        return "All memories cleared";
    }

    @Data
    public static class AddMemoryRequest {
        private String content;
        private String type; // working, episodic, semantic, perceptual
        private String userId;
        private Float importance;
        private Map<String, Object> metadata;
    }

    @Data
    public static class SearchMemoryRequest {
        private String query;
        private List<String> types;
        private int limit;
        private float minImportance;
    }
}
