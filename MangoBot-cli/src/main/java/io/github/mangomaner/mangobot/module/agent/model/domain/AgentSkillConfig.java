package io.github.mangomaner.mangobot.module.agent.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 
 * @TableName agent_skill_config
 */
@TableName(value ="agent_skill_config")
@Data
public class AgentSkillConfig implements Serializable {
    /**
     * 
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 
     */
    @TableField(value = "skill_name")
    private String skillName;

    /**
     * 
     */
    @TableField(value = "description")
    private String description;

    /**
     * 
     */
    @TableField(value = "skill_path")
    private String skillPath;

    /**
     * 
     */
    @TableField(value = "bound_tool_ids")
    private String boundToolIds;

    /**
     * 
     */
    @TableField(value = "enabled")
    private Boolean enabled;

    /**
     * 是否是人格 skill（由导入界面决定）
     */
    @TableField(value = "is_persona")
    private Boolean isPersona;

    /**
     * 启用列表（当前启用的来源）
     */
    @TableField(value = "enabled_list")
    private String enabledList;

    /**
     * 可启用列表（可以被启用的来源）
     */
    @TableField(value = "available_list")
    private String availableList;

    /**
     * 
     */
    @TableField(value = "create_time")
    private Date createTime;

    /**
     * 
     */
    @TableField(value = "update_time")
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}