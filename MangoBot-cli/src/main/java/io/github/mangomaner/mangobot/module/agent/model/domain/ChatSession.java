package io.github.mangomaner.mangobot.module.agent.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 对话会话（chat_session）
 *
 * <p>v2 升级：废弃 memory_state（上下文由 AgentStateStore + workspace 会话日志承载），
 * 会话以 (bot_id, source, chat_id) 为业务键。
 */
@TableName(value = "chat_session")
@Data
public class ChatSession implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "title")
    private String title;

    /** Bot ID（使用 String 兼容多平台） */
    @TableField(value = "bot_id")
    private String botId;

    /** 聊天 ID（群聊ID/私聊ID，使用 String 兼容多平台） */
    @TableField(value = "chat_id")
    private String chatId;

    @TableField(value = "source")
    private SessionSource source;

    @TableField(value = "create_time")
    private Date createTime;

    @TableField(value = "update_time")
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
