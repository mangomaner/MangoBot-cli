package io.github.mangomaner.mangobot.module.agent.model.vo;

import io.github.mangomaner.mangobot.module.agent.model.dataset.StyleMessage;
import io.github.mangomaner.mangobot.module.agent.model.dataset.StyleRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 风格数据集详情视图（含 roles/messages 明细）。
 */
@Data
@Builder
@Schema(description = "风格数据集详情")
public class StyleDatasetDetailVO {

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

    @Schema(description = "导入来源会话 id（人工创建为空）")
    private Long sourceSessionId;

    @Schema(description = "角色列表")
    private List<StyleRole> roles;

    @Schema(description = "消息列表")
    private List<StyleMessage> messages;

    @Schema(description = "创建时间")
    private Long createdAt;

    @Schema(description = "更新时间")
    private Long updatedAt;
}
