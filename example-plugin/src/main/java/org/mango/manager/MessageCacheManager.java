package org.mango.manager;

import org.mango.model.ChatMessage;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

// 复读判断
public class MessageCacheManager {

    // 群ID -> 最近的消息队列
    private final Map<String, Queue<MessageCacheItem>> cache = new ConcurrentHashMap<>();
    private final Map<String, MessageCacheItem> lastRepeatMessage = new ConcurrentHashMap<>();
    private final int MAX_CACHE_SIZE; // 每个群缓存的最大消息数

    public MessageCacheManager(int maxCacheSize) {
        this.MAX_CACHE_SIZE = maxCacheSize - 1;
    }

    /**
     * 判断当前消息是否与队列中“所有”消息都一致
     */
    public boolean isDuplicateMessage(ChatMessage chatMessage) {
        String groupId = chatMessage.getGroupId();
        Queue<MessageCacheItem> queue = cache.computeIfAbsent(groupId, k -> new ArrayDeque<>());

        // 如果队列为空，直接加入当前消息并返回 false（不是重复）
        if (queue.isEmpty() || queue.size() < MAX_CACHE_SIZE) {
            queue.offer(new MessageCacheItem(chatMessage.getMessage(), chatMessage.getFile(), chatMessage.getTargetId()));
            return false;
        }

        // 检查队列中每一个消息是否都与当前消息一致
        boolean allMatch = true;
        for (MessageCacheItem item : queue) {
            if (!Objects.equals(chatMessage.getMessage(), item.message) ||
                    !Objects.equals(chatMessage.getFile(), item.file) ||
                    !Objects.equals(chatMessage.getTargetId(), item.targetId)) {
                allMatch = false;
                break;
            }
        }

        if (allMatch) {
            MessageCacheItem item = lastRepeatMessage.getOrDefault(groupId, new MessageCacheItem());
            // 如果上次重复的消息为空/与这次不一样
            if (lastRepeatMessage.isEmpty() || !(Objects.equals(chatMessage.getMessage(), item.message) &&
                    Objects.equals(chatMessage.getFile(), item.file) &&
                    Objects.equals(chatMessage.getTargetId(), item.targetId))) {
                lastRepeatMessage.put(groupId, new MessageCacheItem(chatMessage.getMessage(), chatMessage.getFile(), chatMessage.getTargetId()));
                return true;
            }
            return false;
        } else {
            // 有不一样的，添加当前消息进队列
            queue.offer(new MessageCacheItem(chatMessage.getMessage(), chatMessage.getFile(), chatMessage.getTargetId()));

            // 控制队列大小
            while (queue.size() > MAX_CACHE_SIZE) {
                queue.poll();
            }
            return false;
        }
    }

    /**
     * 清空某个群组的消息缓存（可选）
     */
    public void clearGroupCache(String groupId) {
        Queue<MessageCacheItem> queue = cache.get(groupId);
        if (queue != null) {
            queue.clear();
        }
    }

    // 内部类：缓存项
    private static class MessageCacheItem {
        String message;
        String file;
        String targetId;

        public MessageCacheItem(){}

        public MessageCacheItem(String message, String file, String targetId) {
            this.message = message;
            this.file = file;
            this.targetId = targetId;
        }
    }
}