package io.github.mangomaner.mangobot.module.agent.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 提示词规范（prompt_norm）：三层提示词的 DB 存储。
 *
 * <p>继承模型下"惰性生成"：未编辑的作用域不落行，渲染器自动继承上一级，直至 resource 出厂模板。
 * 每行唯一约束为 (scope, source, bot_id, chat_id, layer)。
 */
@TableName(value = "prompt_norm")
@Data
public class PromptNorm implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 作用域：GLOBAL / BOT / SESSION */
    private String scope;

    /** Bot ID（非 GLOBAL 时必填，String 兼容多平台） */
    private String botId;

    /** 来源：group / private / web */
    private String source;

    /** 群聊/私聊 ID（仅 SESSION 时必填） */
    private String chatId;

    /** 层：framework / persona / behavior */
    private String layer;

    /** Markdown 内容 */
    private String content;

    /** 是否启用 */
    private Integer enabled;

    private Long createdAt;

    private Long updatedAt;
}
