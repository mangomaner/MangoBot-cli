package io.github.mangomaner.mangobot.module.agent.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 风格数据集注入关联（style_injection）：作用域 → 数据集。
 *
 * <p>解析链：SESSION → BOT → GLOBAL → 不注入。每作用域唯一指定一个数据集
 * （唯一约束 (scope, source, bot_id, chat_id)），切换即覆盖。
 */
@TableName(value = "style_injection")
@Data
public class StyleInjection implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 作用域：GLOBAL / BOT / SESSION */
    private String scope;

    /** 来源：group / private */
    private String source;

    /** Bot ID（非 GLOBAL 时必填） */
    private String botId;

    /** 群聊/私聊 ID（仅 SESSION 时必填） */
    private String chatId;

    /** 指向 style_dataset.id */
    private Long datasetId;

    private Long createdAt;

    private Long updatedAt;
}
