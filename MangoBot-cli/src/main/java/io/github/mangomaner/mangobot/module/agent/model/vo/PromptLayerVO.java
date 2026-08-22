package io.github.mangomaner.mangobot.module.agent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 编辑页单层提示词视图。
 *
 * <p>effectiveScope 表示当前生效内容实际来自哪一级（SESSION/BOT/GLOBAL/TEMPLATE），
 * customized 表示请求作用域下是否已自定义（决定显示"已自定义/继承/出厂模板"）。
 */
@Data
@Builder
@Schema(description = "编辑页单层提示词")
public class PromptLayerVO {

    @Schema(description = "层：framework / persona / behavior")
    private String layer;

    @Schema(description = "层标题：框架规范 / 人格 / 行为")
    private String title;

    @Schema(description = "当前生效内容（按继承链解析后的最终内容，作为编辑起点）")
    private String content;

    @Schema(description = "生效来源：SESSION / BOT / GLOBAL / TEMPLATE")
    private String effectiveScope;

    @Schema(description = "当前请求作用域下是否已自定义")
    private boolean customized;

    @Schema(description = "出厂模板内容（仅 L1 框架规范返回，用于高风险预览）")
    private String template;
}
