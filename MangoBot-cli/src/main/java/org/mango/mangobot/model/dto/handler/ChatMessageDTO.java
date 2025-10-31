package org.mango.mangobot.model.dto.handler;

import lombok.Data;
import org.mango.mangobot.model.QQ.ReceiveMessageSegment;

import java.util.List;

@Data
public class ChatMessageDTO {
    private Long id;
    private Integer messageId;
    private String groupId;
    private String userId;
    private String targetId;
    private String message;
    private String imageUrl;
    private String file;
    private Long timestamp; // 建议使用时间戳（毫秒）
    List<ReceiveMessageSegment> ReceiveMessageSegment;
}