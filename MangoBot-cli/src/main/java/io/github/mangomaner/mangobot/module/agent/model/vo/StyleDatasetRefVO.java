package io.github.mangomaner.mangobot.module.agent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 风格数据集注入引用：生效列表中的单个数据集。
 */
@Data
@Builder
@Schema(description = "风格数据集注入引用")
public class StyleDatasetRefVO {

    @Schema(description = "数据集 ID")
    private Long datasetId;

    @Schema(description = "数据集名称")
    private String datasetName;

    @Schema(description = "该数据集实际生效于哪一级：SESSION / BOT / GLOBAL")
    private String effectiveScope;
}
