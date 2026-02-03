package io.github.mangomaner.mangobot.memory.storage;

import io.github.mangomaner.mangobot.memory.core.MemoryConfig;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.util.*;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

/**
 * Neo4j 图数据库存储 (嵌入式)
 * 用于语义记忆的知识图谱存储
 */
@Slf4j
@Component
public class Neo4jGraphStore {

    private final MemoryConfig config;
    private DatabaseManagementService managementService;
    private GraphDatabaseService graphDb;

    public Neo4jGraphStore(MemoryConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        Path path = config.getNeo4jPath();
        log.info("Initializing Neo4jGraphStore at: {}", path);

        try {
            this.managementService = new DatabaseManagementServiceBuilder(path)
                    .setConfig(GraphDatabaseSettings.keep_logical_logs, "100M size")
                    .build();
            this.graphDb = managementService.database(DEFAULT_DATABASE_NAME);
            registerShutdownHook(managementService);
        } catch (Exception e) {
            log.error("Failed to initialize Neo4j", e);
        }
    }

    @PreDestroy
    public void close() {
        if (managementService != null) {
            log.info("Shutting down Neo4j...");
            managementService.shutdown();
        }
    }

    private static void registerShutdownHook(final DatabaseManagementService managementService) {
        Runtime.getRuntime().addShutdownHook(new Thread(managementService::shutdown));
    }

    /**
     * 添加概念节点
     */
    public void addConcept(String name, String description) {
        try (Transaction tx = graphDb.beginTx()) {
            Label label = Label.label("Concept");
            Node node = tx.findNode(label, "name", name);
            if (node == null) {
                node = tx.createNode(label);
                node.setProperty("name", name);
            }
            if (description != null) {
                node.setProperty("description", description);
            }
            tx.commit();
        }
    }

    /**
     * 添加关系
     */
    public void addRelation(String fromConcept, String toConcept, String relationType) {
        try (Transaction tx = graphDb.beginTx()) {
            Label label = Label.label("Concept");
            Node from = tx.findNode(label, "name", fromConcept);
            Node to = tx.findNode(label, "name", toConcept);

            if (from != null && to != null) {
                RelationshipType type = RelationshipType.withName(relationType);
                boolean exists = false;
                for (Relationship rel : from.getRelationships(Direction.OUTGOING, type)) {
                    if (rel.getEndNode().equals(to)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    from.createRelationshipTo(to, type);
                }
            }
            tx.commit();
        }
    }

    /**
     * 查找相关概念 (图相似度计算的基础)
     * 简单实现：查找 N 跳以内的邻居
     */
    public List<String> findRelatedConcepts(String conceptName, int depth) {
        List<String> related = new ArrayList<>();
        try (Transaction tx = graphDb.beginTx()) {
            Node startNode = tx.findNode(Label.label("Concept"), "name", conceptName);
            if (startNode != null) {
                traverse(startNode, 0, depth, related, new HashSet<>());
            }
        }
        return related;
    }

    private void traverse(Node node, int currentDepth, int maxDepth, List<String> result, Set<String> visited) {
        if (currentDepth >= maxDepth || visited.contains(node.getElementId())) {
            return;
        }
        visited.add(node.getElementId());
        
        if (currentDepth > 0) { // 不包含自身
            result.add((String) node.getProperty("name"));
        }

        for (Relationship rel : node.getRelationships(Direction.OUTGOING)) {
            traverse(rel.getEndNode(), currentDepth + 1, maxDepth, result, visited);
        }
    }
    
    /**
     * 计算两个概念之间的图相似度
     * 简单实现：如果直接相连为 1.0，隔一跳 0.5，否则 0
     */
    public float calculateGraphSimilarity(String conceptA, String conceptB) {
        try (Transaction tx = graphDb.beginTx()) {
            Node nodeA = tx.findNode(Label.label("Concept"), "name", conceptA);
            Node nodeB = tx.findNode(Label.label("Concept"), "name", conceptB);
            
            if (nodeA == null || nodeB == null) return 0.0f;
            
            // 检查直接连接
            for (Relationship rel : nodeA.getRelationships(Direction.BOTH)) {
                if (rel.getOtherNode(nodeA).equals(nodeB)) {
                    return 1.0f;
                }
            }
            
            // 检查 2 跳连接
            for (Relationship rel : nodeA.getRelationships(Direction.BOTH)) {
                Node neighbor = rel.getOtherNode(nodeA);
                for (Relationship rel2 : neighbor.getRelationships(Direction.BOTH)) {
                    if (rel2.getOtherNode(neighbor).equals(nodeB)) {
                        return 0.5f;
                    }
                }
            }
        }
        return 0.0f;
    }
    
    public void clear() {
        try (Transaction tx = graphDb.beginTx()) {
            tx.execute("MATCH (n) DETACH DELETE n");
            tx.commit();
        }
    }
}
