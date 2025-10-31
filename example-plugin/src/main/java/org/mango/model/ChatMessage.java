package org.mango.model;

import lombok.Data;

@Data
public class ChatMessage {
    private Long id;
    private Integer messageId;
    private String groupId;
    private String userId;
    private String targetId;
    private String message;
    private String imageUrl;
    private Long timestamp; // 建议使用时间戳（毫秒）
    private String file;
}