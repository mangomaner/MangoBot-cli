package io.github.mangomaner.mangobot.manager.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.manager.GlobalConfigCache;
import io.github.mangomaner.mangobot.manager.event.ConfigChangeEvent;
import io.github.mangomaner.mangobot.model.onebot.event.Event;
import io.github.mangomaner.mangobot.model.onebot.event.message.GroupMessageEvent;
import io.github.mangomaner.mangobot.model.onebot.event.message.PrivateMessageEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 黑白名单过滤器
 * 负责根据全局配置过滤消息事件
 */
@Component
@Slf4j
public class BlackWhiteListFilter implements EventFilter {

    @Resource
    private ObjectMapper objectMapper;

    // 缓存解析后的黑白名单配置 (BotId -> List/Value)
    private volatile Map<Long, List<Long>> groupWhitelistCache = null;
    private volatile Map<Long, List<Long>> groupBlacklistCache = null;
    private volatile Integer groupEnableListCache = null;
    private volatile Map<Long, List<Long>> privateWhitelistCache = null;
    private volatile Map<Long, List<Long>> privateBlacklistCache = null;
    private volatile Integer privateEnableListCache = null;

    // 配置Key常量
    private static final String KEY_GROUP_WHITELIST = "main.QQ.group.whitelist";
    private static final String KEY_GROUP_BLACKLIST = "main.QQ.group.blacklist";
    private static final String KEY_GROUP_ENABLE = "main.QQ.group.enable_list";
    private static final String KEY_PRIVATE_WHITELIST = "main.QQ.private.whitelist";
    private static final String KEY_PRIVATE_BLACKLIST = "main.QQ.private.blacklist";
    private static final String KEY_PRIVATE_ENABLE = "main.QQ.private.enable_list";

    @Override
    public boolean allow(Event event) {
        long selfId = event.getSelfId();

        if (event instanceof GroupMessageEvent) {
            GroupMessageEvent groupEvent = (GroupMessageEvent) event;
            long groupId = groupEvent.getGroupId();

            // 检查启用状态
            Integer enableMap = getGroupEnableListCache();
            int enable = enableMap != null ? enableMap : 0;

            if (enable == 1) {
                // 启用黑白名单
                Map<Long, List<Long>> whitelistMap = getGroupWhitelistCache();
                List<Long> whitelist = whitelistMap != null ? whitelistMap.get(selfId) : null;

                // 如果有白名单，必须在白名单中
                if (whitelist != null && !whitelist.isEmpty()) {
                    if (!whitelist.contains(groupId)) {
                        log.debug("群组 {} 不在白名单中，忽略消息。", groupId);
                        return false;
                    }
                } else {
                    // 没有白名单，检查黑名单
                    Map<Long, List<Long>> blacklistMap = getGroupBlacklistCache();
                    List<Long> blacklist = blacklistMap != null ? blacklistMap.get(selfId) : null;
                    if (blacklist != null && blacklist.contains(groupId)) {
                        log.debug("群组 {} 在黑名单中，忽略消息。", groupId);
                        return false;
                    }
                }
            }
        } else if (event instanceof PrivateMessageEvent) {
            PrivateMessageEvent privateEvent = (PrivateMessageEvent) event;
            long userId = privateEvent.getUserId();

            // 检查启用状态
            Integer enableMap = getPrivateEnableListCache();
            int enable = enableMap != null ? enableMap : 0;
            if (enable == 1) {
                // 启用黑白名单
                Map<Long, List<Long>> whitelistMap = getPrivateWhitelistCache();
                List<Long> whitelist = whitelistMap != null ? whitelistMap.get(selfId) : null;

                if (whitelist != null && !whitelist.isEmpty()) {
                    if (!whitelist.contains(userId)) {
                        log.debug("用户 {} 不在私聊白名单中，忽略消息。", userId);
                        return false;
                    }
                } else {
                    Map<Long, List<Long>> blacklistMap = getPrivateBlacklistCache();
                    List<Long> blacklist = blacklistMap != null ? blacklistMap.get(selfId) : null;
                    if (blacklist != null && blacklist.contains(userId)) {
                        log.debug("用户 {} 在私聊黑名单中，忽略消息。", userId);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void handleConfigChange(ConfigChangeEvent event) {
        String key = event.getKey();
        if (key == null) return;

        switch (key) {
            case KEY_GROUP_WHITELIST: groupWhitelistCache = null; break;
            case KEY_GROUP_BLACKLIST: groupBlacklistCache = null; break;
            case KEY_GROUP_ENABLE: groupEnableListCache = null; break;
            case KEY_PRIVATE_WHITELIST: privateWhitelistCache = null; break;
            case KEY_PRIVATE_BLACKLIST: privateBlacklistCache = null; break;
            case KEY_PRIVATE_ENABLE: privateEnableListCache = null; break;
        }
    }

    // --- 懒加载获取缓存方法 ---

    private Map<Long, List<Long>> getGroupWhitelistCache() {
        if (groupWhitelistCache == null) {
            synchronized (this) {
                if (groupWhitelistCache == null) {
                    groupWhitelistCache = parseLongListConfig(KEY_GROUP_WHITELIST);
                }
            }
        }
        return groupWhitelistCache;
    }

    private Map<Long, List<Long>> getGroupBlacklistCache() {
        if (groupBlacklistCache == null) {
            synchronized (this) {
                if (groupBlacklistCache == null) {
                    groupBlacklistCache = parseLongListConfig(KEY_GROUP_BLACKLIST);
                }
            }
        }
        return groupBlacklistCache;
    }

    private Integer getGroupEnableListCache() {
        if (groupEnableListCache == null) {
            synchronized (this) {
                if (groupEnableListCache == null) {
                    groupEnableListCache = parseIntegerConfig(KEY_GROUP_ENABLE);
                }
            }
        }
        return groupEnableListCache;
    }

    private Map<Long, List<Long>> getPrivateWhitelistCache() {
        if (privateWhitelistCache == null) {
            synchronized (this) {
                if (privateWhitelistCache == null) {
                    privateWhitelistCache = parseLongListConfig(KEY_PRIVATE_WHITELIST);
                }
            }
        }
        return privateWhitelistCache;
    }

    private Map<Long, List<Long>> getPrivateBlacklistCache() {
        if (privateBlacklistCache == null) {
            synchronized (this) {
                if (privateBlacklistCache == null) {
                    privateBlacklistCache = parseLongListConfig(KEY_PRIVATE_BLACKLIST);
                }
            }
        }
        return privateBlacklistCache;
    }

    private Integer getPrivateEnableListCache() {
        if (privateEnableListCache == null) {
            synchronized (this) {
                if (privateEnableListCache == null) {
                    privateEnableListCache = parseIntegerConfig(KEY_PRIVATE_ENABLE);
                }
            }
        }
        return privateEnableListCache;
    }

    private Map<Long, List<Long>> parseLongListConfig(String key) {
        String json = GlobalConfigCache.getConfig(key);
        if (json == null || json.isEmpty() || "{}".equals(json)) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<Long, List<Long>>>() {});
        } catch (Exception e) {
            log.error("解析配置 {} 失败: {}", key, e.getMessage());
            return new HashMap<>();
        }
    }

    private Integer parseIntegerConfig(String key) {
        String json = GlobalConfigCache.getConfig(key);
        if (json == null || json.isEmpty()) return null;
        // 尝试直接解析为 Integer
        return Integer.parseInt(json);
    }
}
