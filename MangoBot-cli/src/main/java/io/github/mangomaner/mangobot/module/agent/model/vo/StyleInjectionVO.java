package io.github.mangomaner.mangobot.module.agent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 风格数据集注入视图（当前作用域解析出的生效注入集合）。
 *
 * <p>一作用域可注入多个数据集；解析链 SESSION → BOT → GLOBAL，取第一个有注入的作用域，
 * datasets 为该作用域的全部注入。无任何注入时 datasets 为空列表。
 */
@Data
@Builder
@Schema(description = "风格数据集注入信息")
public class StyleInjectionVO {

    @Schema(description = "实际生效作用域：GLOBAL / BOT / SESSION（无注入时为空）")
    private String scope;

    @Schema(description = "来源：group / private")
    private String source;

    @Schema(description = "Bot ID（非 GLOBAL 时）")
    private String botId;

    @Schema(description = "群聊/私聊 ID（仅 SESSION 时）")
    private String chatId;

    @Schema(description = "生效的数据集列表（空 = 未注入）")
    private List<StyleDatasetRefVO> datasets;
}
