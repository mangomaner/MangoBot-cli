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
 * <p>会话以 {@code (bot_id, source, chat_id)} 为业务键，与 claude 的会话键一一对应。
 *
 * <p><b>这张表不承载对话上下文</b>：它只是给 UI 用的会话投影。真正的上下文活在 claude 子进程里，
 * 并由 claude 自己写在 {@code data/claude-work/<会话>/claude-config/projects/} 下的转录里，
 * 进程重建时靠 {@code -c} 续上。
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
