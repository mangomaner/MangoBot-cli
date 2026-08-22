package io.github.mangomaner.mangobot.module.agent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设置/更换风格数据集注入请求。
 */
@Data
@Schema(description = "设置风格数据集注入请求")
public class StyleInjectionRequest {

    @Schema(description = "作用域：GLOBAL / BOT / SESSION", requiredMode = Schema.RequiredMode.REQUIRED)
    private String scope;

    @Schema(description = "来源：group / private", requiredMode = Schema.RequiredMode.REQUIRED)
    private String source;

    @Schema(description = "Bot ID（非 GLOBAL 时必填）")
    private String botId;

    @Schema(description = "群聊/私聊 ID（仅 SESSION 时必填）")
    private String chatId;

    @Schema(description = "要注入的数据集 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long datasetId;
}
