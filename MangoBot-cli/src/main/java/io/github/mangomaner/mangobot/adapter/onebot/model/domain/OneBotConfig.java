package io.github.mangomaner.mangobot.adapter.onebot.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@TableName(value = "onebot_config")
@Data
public class OneBotConfig implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String url;

    private String token;

    private String protocolType;

    private Integer enabled;

    private Integer connectionStatus;

    private String description;

    private Long createdAt;

    private Long updatedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
