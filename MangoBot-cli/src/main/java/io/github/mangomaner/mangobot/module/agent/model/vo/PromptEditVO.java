package io.github.mangomaner.mangobot.module.agent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 统一提示词编辑页聚合视图：一次请求返回三层全部编辑信息。
 */
@Data
@Builder
@Schema(description = "提示词编辑页聚合视图")
public class PromptEditVO {

    @Schema(description = "编辑作用域：GLOBAL / BOT / SESSION")
    private String scope;

    @Schema(description = "Bot ID（非 GLOBAL 时）")
    private String botId;

    @Schema(description = "来源：group / private / web")
    private String source;

    @Schema(description = "群聊/私聊 ID（仅 SESSION 时）")
    private String chatId;

    @Schema(description = "三层提示词")
    private List<PromptLayerVO> layers;
}
