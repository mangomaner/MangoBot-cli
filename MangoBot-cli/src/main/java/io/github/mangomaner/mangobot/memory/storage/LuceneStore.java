package io.github.mangomaner.mangobot.memory.storage;

import io.github.mangomaner.mangobot.memory.core.MemoryConfig;
import io.github.mangomaner.mangobot.memory.core.MemoryItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Lucene 存储后端
 * 支持向量检索 (KNN) 和 全文检索 (BM25)
 */
@Slf4j
@Component
public class LuceneStore {

    private final MemoryConfig config;
    private Directory directory;
    private Analyzer analyzer;
    private IndexWriter indexWriter;
    private SearcherManager searcherManager;

    private static final String FIELD_ID = "id";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_VECTOR = "vector";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_IMPORTANCE = "importance";
    private static final String FIELD_USER_ID = "userId";

    public LuceneStore(MemoryConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() throws IOException {
        Path path = config.getLucenePath();
        log.info("Initializing LuceneStore at: {}", path);
        
        this.directory = FSDirectory.open(path);
        
        // 尝试使用中文分词，如果依赖缺失则回退到标准分词
        try {
            this.analyzer = new SmartChineseAnalyzer();
        } catch (Throwable e) {
            log.warn("SmartChineseAnalyzer not available, falling back to StandardAnalyzer", e);
            this.analyzer = new StandardAnalyzer();
        }

        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        
        this.indexWriter = new IndexWriter(directory, iwc);
        this.searcherManager = new SearcherManager(indexWriter, new SearcherFactory());
    }

    @PreDestroy
    public void close() {
        try {
            if (searcherManager != null) searcherManager.close();
            if (indexWriter != null) indexWriter.close();
            if (directory != null) directory.close();
        } catch (IOException e) {
            log.error("Error closing LuceneStore", e);
        }
    }

    /**
     * 添加或更新记忆
     */
    public void addMemory(MemoryItem item) throws IOException {
        Document doc = new Document();
        doc.add(new StringField(FIELD_ID, item.getId(), Field.Store.YES));
        doc.add(new TextField(FIELD_CONTENT, item.getContent(), Field.Store.YES));
        doc.add(new StringField(FIELD_TYPE, item.getMemoryType(), Field.Store.YES));
        doc.add(new StringField(FIELD_USER_ID, item.getUserId(), Field.Store.YES));
        
        long ts = item.getTimestamp().toEpochSecond(ZoneOffset.UTC);
        doc.add(new StoredField(FIELD_TIMESTAMP, ts));
        doc.add(new StoredField(FIELD_IMPORTANCE, item.getImportance()));

        if (item.getEmbedding() != null && item.getEmbedding().length > 0) {
            doc.add(new KnnFloatVectorField(FIELD_VECTOR, item.getEmbedding(), VectorSimilarityFunction.COSINE));
        }

        // 使用 updateDocument 以便覆盖旧 ID
        indexWriter.updateDocument(new Term(FIELD_ID, item.getId()), doc);
        indexWriter.commit(); // 立即提交以便搜索可见 (生产环境可优化为定时提交)
        searcherManager.maybeRefresh();
    }

    /**
     * 删除记忆
     */
    public void removeMemory(String id) throws IOException {
        indexWriter.deleteDocuments(new Term(FIELD_ID, id));
        indexWriter.commit();
        searcherManager.maybeRefresh();
    }
    
    /**
     * 清空所有
     */
    public void clear() throws IOException {
        indexWriter.deleteAll();
        indexWriter.commit();
        searcherManager.maybeRefresh();
    }

    /**
     * 混合检索
     * @param vector 查询向量
     * @param text 查询文本
     * @param limit 数量限制
     * @param memoryType 过滤类型 (可选)
     */
    public List<SearchResult> search(float[] vector, String text, int limit, String memoryType, float minImportance) {
        List<SearchResult> results = new ArrayList<>();
        IndexSearcher searcher = null;
        try {
            searcherManager.maybeRefresh();
            searcher = searcherManager.acquire();

            BooleanQuery.Builder builder = new BooleanQuery.Builder();

            // 1. 向量检索 (KNN)
            if (vector != null && vector.length > 0) {
                Query knnQuery = new KnnFloatVectorQuery(FIELD_VECTOR, vector, limit);
                builder.add(knnQuery, BooleanClause.Occur.SHOULD);
            }

            // 2. 文本检索 (BM25)
            if (text != null && !text.isEmpty()) {
                QueryParser parser = new QueryParser(FIELD_CONTENT, analyzer);
                Query textQuery = parser.parse(QueryParser.escape(text)); // 简单转义
                builder.add(textQuery, BooleanClause.Occur.SHOULD);
            }
            
            // 3. 过滤器
            if (memoryType != null) {
                builder.add(new TermQuery(new Term(FIELD_TYPE, memoryType)), BooleanClause.Occur.FILTER);
            }

            // 执行搜索
            TopDocs topDocs = searcher.search(builder.build(), limit);
            
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                float score = scoreDoc.score;
                
                MemoryItem item = convertToMemoryItem(doc);
                if (item.getImportance() >= minImportance) {
                    results.add(new SearchResult(item, score));
                }
            }

        } catch (Exception e) {
            log.error("Search failed", e);
        } finally {
            if (searcher != null) {
                try {
                    searcherManager.release(searcher);
                } catch (IOException e) {
                    log.error("Error releasing searcher", e);
                }
            }
        }
        return results;
    }

    private MemoryItem convertToMemoryItem(Document doc) {
        long ts = doc.getField(FIELD_TIMESTAMP).numericValue().longValue();
        
        return MemoryItem.builder()
                .id(doc.get(FIELD_ID))
                .content(doc.get(FIELD_CONTENT))
                .memoryType(doc.get(FIELD_TYPE))
                .userId(doc.get(FIELD_USER_ID))
                .timestamp(LocalDateTime.ofEpochSecond(ts, 0, ZoneOffset.UTC))
                .importance(doc.getField(FIELD_IMPORTANCE).numericValue().floatValue())
                .build();
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SearchResult {
        private MemoryItem item;
        private float score;
    }
    
    public long count() {
        IndexSearcher searcher = null;
        try {
            searcher = searcherManager.acquire();
            return searcher.getIndexReader().numDocs();
        } catch (IOException e) {
            return 0;
        } finally {
            if (searcher != null) {
                try {
                    searcherManager.release(searcher);
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
