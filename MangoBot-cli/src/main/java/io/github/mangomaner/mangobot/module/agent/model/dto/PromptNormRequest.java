package io.github.mangomaner.mangobot.module.agent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 保存提示词规范请求（scope 下的单个 layer）。
 */
@Data
@Schema(description = "保存提示词规范请求")
public class PromptNormRequest {

    @Schema(description = "作用域：GLOBAL / BOT / SESSION", requiredMode = Schema.RequiredMode.REQUIRED)
    private String scope;

    @Schema(description = "Bot ID（非 GLOBAL 时必填）")
    private String botId;

    @Schema(description = "来源：group / private / web", requiredMode = Schema.RequiredMode.REQUIRED)
    private String source;

    @Schema(description = "群聊/私聊 ID（仅 SESSION 时必填）")
    private String chatId;

    @Schema(description = "层：framework / persona / behavior", requiredMode = Schema.RequiredMode.REQUIRED)
    private String layer;

    @Schema(description = "Markdown 内容")
    private String content;
}
