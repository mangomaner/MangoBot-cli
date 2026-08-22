package io.github.mangomaner.mangobot.module.agent.model.vo;

import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 对话会话视图对象
 */
@Data
@Schema(description = "对话会话信息")
public class ChatSessionVO {

    @Schema(description = "会话ID")
    private Integer id;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "Bot ID")
    private String botId;

    @Schema(description = "群/私聊ID")
    private String chatId;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;

    @Schema(description = "消息数量")
    private Long messageCount;

    @Schema(description = "会话来源")
    private SessionSource source;
}
