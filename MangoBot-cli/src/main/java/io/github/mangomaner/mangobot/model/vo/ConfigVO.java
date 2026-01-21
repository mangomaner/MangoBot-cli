package io.github.mangomaner.mangobot.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "配置信息返回")
public class ConfigVO {

    @Schema(description = "配置ID")
    private Long id;

    @Schema(description = "配置键")
    private String configKey;

    @Schema(description = "配置值")
    private String configValue;

    @Schema(description = "配置类型")
    private String configType;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "是否可编辑")
    private Boolean editable;

    @Schema(description = "创建时间")
    private Integer createdAt;

    @Schema(description = "更新时间")
    private Integer updatedAt;
}
