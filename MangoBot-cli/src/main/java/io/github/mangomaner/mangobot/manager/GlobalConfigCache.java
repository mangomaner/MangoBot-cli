package io.github.mangomaner.mangobot.manager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 全局配置缓存
 * 提供高性能的配置读取，避免循环依赖
 */
@Component
@Slf4j
public class GlobalConfigCache {

    /**
     * 使用 Caffeine 缓存
     * initialCapacity: 初始容量
     * maximumSize: 最大容量，防止内存溢出
     * recordStats: 开启统计（可选）
     * 
     * 生产环境最佳实践调整：
     * 1. 移除了 expireAfterWrite：配置数据应当持久有效，不应因时间流逝而过期。
     * 2. 保留 maximumSize：作为兜底策略，防止异常情况下的内存溢出。
     * 3. 数据一致性保障：依赖 "事件驱动更新(实时)" + "定时全量刷新(兜底)" 双重机制。
     */
    private static final Cache<String, String> CONFIG_CACHE = Caffeine.newBuilder()
            .initialCapacity(100)
            .maximumSize(10000)
            .recordStats()
            .build();

    /**
     * 更新缓存
     * @param key 配置键
     * @param value 配置值
     */
    public void put(String key, String value) {
        if (key != null && value != null) {
            CONFIG_CACHE.put(key, value);
            log.debug("配置缓存已更新: {} = {}", key, value);
        }
    }

    /**
     * 获取配置值
     * @param key 配置键
     * @return 配置值
     */
    public String get(String key) {
        return CONFIG_CACHE.getIfPresent(key);
    }
    
    /**
     * 获取配置值（静态方法，方便插件调用）
     * @param key 配置键
     * @return 配置值
     */
    public static String getConfig(String key) {
        return CONFIG_CACHE.getIfPresent(key);
    }

    /**
     * 移除配置
     * @param key 配置键
     */
    public void remove(String key) {
        CONFIG_CACHE.invalidate(key);
    }
    
    /**
     * 清空并重新加载所有配置（通常由Service调用）
     */
    public void refreshAll(Map<String, String> configs) {
        CONFIG_CACHE.putAll(configs);
        log.info("全局配置缓存已刷新，共加载 {} 条配置", configs.size());
        // 打印缓存统计信息（调试用）
        // log.info("Cache Stats: {}", CONFIG_CACHE.stats());
    }
}
