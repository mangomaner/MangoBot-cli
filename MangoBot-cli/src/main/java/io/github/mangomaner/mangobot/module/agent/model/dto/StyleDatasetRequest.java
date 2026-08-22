package io.github.mangomaner.mangobot.module.agent.model.dto;

import io.github.mangomaner.mangobot.module.agent.model.dataset.StyleMessage;
import io.github.mangomaner.mangobot.module.agent.model.dataset.StyleRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 人工创建 / 编辑风格数据集请求。
 */
@Data
@Schema(description = "风格数据集新建/编辑请求")
public class StyleDatasetRequest {

    @Schema(description = "数据集 ID（编辑时必填）")
    private Long id;

    @Schema(description = "数据集名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "来源：group / private", requiredMode = Schema.RequiredMode.REQUIRED)
    private String source;

    @Schema(description = "角色列表（至少 1 个）")
    private List<StyleRole> roles;

    @Schema(description = "消息列表（roleName 必须存在于 roles）")
    private List<StyleMessage> messages;
}
