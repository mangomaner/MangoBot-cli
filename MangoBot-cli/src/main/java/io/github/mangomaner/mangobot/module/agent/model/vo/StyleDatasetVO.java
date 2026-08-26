package io.github.mangomaner.mangobot.module.agent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 风格数据集列表视图（不含 roles/messages 明细）。
 */
@Data
@Builder
@Schema(description = "风格数据集列表项")
public class StyleDatasetVO {

    @Schema(description = "数据集 ID")
    private Long id;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "来源：group / private")
    private String source;

    @Schema(description = "创建类型：created / imported")
    private String createType;

    @Schema(description = "数据集类型：chatlog / character")
    private String datasetType;

    @Schema(description = "导入来源会话 id（人工创建为空）")
    private Long sourceSessionId;

    @Schema(description = "角色数")
    private int roleCount;

    @Schema(description = "消息数")
    private int messageCount;

    @Schema(description = "创建时间")
    private Long createdAt;

    @Schema(description = "更新时间")
    private Long updatedAt;
}
