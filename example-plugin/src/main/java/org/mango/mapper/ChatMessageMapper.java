package org.mango.mapper;

import org.apache.ibatis.annotations.Param;
import org.mango.model.ChatMessage;
import java.util.List;
import java.util.Map;

public interface ChatMessageMapper {

    /**
     * 批量插入消息
     * @param messages
     * @return
     */
    int batchInsert(List<ChatMessage> messages);

    /**
     * 插入一条消息
     */
    void insert(ChatMessage chatMessage);

    /**
     * 查询所有消息
     */
    List<ChatMessage> selectAll();

    /**
     * 按群组ID查询消息
     */
    List<ChatMessage> selectByGroupId(String groupId);

    /**
     * 按用户ID查询消息
     */
    List<ChatMessage> selectByUserId(String userId);

    /**
     * 按时间范围查询消息
     */
    List<ChatMessage> selectByTimestampRange(Map<String, Object> params);

    /**
     * 按关键字模糊查询消息内容
     */
    List<ChatMessage> selectByKeyword(String keyword, String groupId);

    List<ChatMessage> selectByMessageAndFile(
            @Param("groupId") String groupId,
            @Param("message") String message,
            @Param("file") String file);

    ChatMessage selectNextMessageAfter(
            @Param("groupId") String groupId,
            @Param("timestamp") Long timestamp);
}






