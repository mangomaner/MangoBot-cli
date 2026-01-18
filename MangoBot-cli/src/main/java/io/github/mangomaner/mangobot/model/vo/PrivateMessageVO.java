package io.github.mangomaner.mangobot.model.vo;

import io.github.mangomaner.mangobot.model.onebot.segment.MessageSegment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "私聊消息返回")
public class PrivateMessageVO {

    @Schema(description = "消息ID")
    private Long id;

    @Schema(description = "Bot ID")
    private Long botId;

    @Schema(description = "好友ID")
    private Long friendId;

    @Schema(description = "消息ID")
    private Integer messageId;

    @Schema(description = "发送者ID")
    private Long senderId;

    @Schema(description = "消息段列表")
    private List<MessageSegment> messageSegments;

    @Schema(description = "消息时间")
    private Long messageTime;

    @Schema(description = "是否删除")
    private Integer isDelete;

    @Schema(description = "解析后的消息")
    private String parseMessage;
}
