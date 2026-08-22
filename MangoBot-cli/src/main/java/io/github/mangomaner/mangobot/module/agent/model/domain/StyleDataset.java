package io.github.mangomaner.mangobot.module.agent.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 风格数据集（style_dataset）：公共数据中心，教 agent 向回复工具传参的对话剧本。
 *
 * <p>roles / messages 以 JSON 字符串内嵌存储（无需分表），整体按 source（group/private）归类；
 * 数据为公共资源，不区分 bot。人工创建（created）或从会话消息导入（imported）。
 */
@TableName(value = "style_dataset")
@Data
public class StyleDataset implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /** 来源：group / private */
    private String source;

    /** 创建类型：created / imported */
    private String createType;

    /** 导入时记录来源会话 id（人工创建为空） */
    private Long sourceSessionId;

    /** JSON 数组：[{"name":"群友A"},...]，至少 1 个角色 */
    private String roles;

    /** JSON 数组：[{"roleName":"群友A","text":"..."},...]，按序构成对话剧本 */
    private String messages;

    private Long createdAt;

    private Long updatedAt;
}
