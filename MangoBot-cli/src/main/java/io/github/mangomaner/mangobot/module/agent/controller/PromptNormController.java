package io.github.mangomaner.mangobot.module.agent.controller;

import io.github.mangomaner.mangobot.module.agent.model.dto.PromptNormRequest;
import io.github.mangomaner.mangobot.module.agent.model.enums.BotType;
import io.github.mangomaner.mangobot.module.agent.model.enums.PromptLayer;
import io.github.mangomaner.mangobot.module.agent.model.enums.PromptScope;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.PromptEditVO;
import io.github.mangomaner.mangobot.module.agent.service.PromptNormService;
import io.github.mangomaner.mangobot.system.common.BaseResponse;
import io.github.mangomaner.mangobot.system.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提示词规范（L1/L2）控制器：三级作用域的编辑页、保存与恢复默认。
 */
@Slf4j
@RestController
@RequestMapping("/api/prompt/norm")
@RequiredArgsConstructor
public class PromptNormController {

    private final PromptNormService promptNormService;

    @GetMapping("/edit-page")
    @Operation(summary = "获取提示词编辑页", description = "按作用域返回三层提示词的编辑信息（生效内容/生效来源/是否自定义）")
    public BaseResponse<PromptEditVO> getEditPage(
            @Parameter(description = "作用域：GLOBAL / BOT / SESSION", required = true) @RequestParam String scope,
            @Parameter(description = "Bot ID（非 GLOBAL 时必填）") @RequestParam(required = false) String botId,
            @Parameter(description = "来源：group / private / web", required = true) @RequestParam String source,
            @Parameter(description = "群聊/私聊 ID（仅 SESSION 时必填）") @RequestParam(required = false) String chatId,
            @Parameter(description = "预览用 Bot 类型（可选，仅影响编辑页模板地板，不写库）") @RequestParam(required = false) String type) {
        log.info("获取提示词编辑页，scope: {}, source: {}, botId: {}, chatId: {}, type: {}", scope, source, botId, chatId, type);
        return ResultUtils.success(promptNormService.getEditPage(
                PromptScope.fromCode(scope), botId, SessionSource.fromKey(source), chatId, parseBotType(type)));
    }

    /** 解析编辑页 `type` 预览参数；空/非法一律返回 null（即用 bot 实际类型） */
    private BotType parseBotType(String type) {
        if (type == null || !org.springframework.util.StringUtils.hasText(type)) {
            return null;
        }
        try {
            return BotType.fromCode(type);
        } catch (IllegalArgumentException e) {
            log.warn("编辑页预览 type 参数非法: {}", type);
            return null;
        }
    }

    @PostMapping("/save")
    @Operation(summary = "保存提示词层", description = "按作用域 upsert 单层内容；framework 层会校验协议关键词")
    public BaseResponse<Void> save(@Valid @RequestBody PromptNormRequest request) {
        log.info("保存提示词层，scope: {}, layer: {}, source: {}", request.getScope(), request.getLayer(), request.getSource());
        promptNormService.save(request);
        return ResultUtils.success(null);
    }

    @DeleteMapping("/restore")
    @Operation(summary = "恢复默认", description = "删除指定作用域的行，自动回退到上一级继承")
    public BaseResponse<Void> restoreDefault(
            @Parameter(description = "作用域：GLOBAL / BOT / SESSION", required = true) @RequestParam String scope,
            @Parameter(description = "Bot ID（非 GLOBAL 时必填）") @RequestParam(required = false) String botId,
            @Parameter(description = "来源：group / private / web", required = true) @RequestParam String source,
            @Parameter(description = "群聊/私聊 ID（仅 SESSION 时必填）") @RequestParam(required = false) String chatId,
            @Parameter(description = "层：framework / persona / behavior", required = true) @RequestParam String layer) {
        log.info("恢复提示词默认，scope: {}, layer: {}, source: {}, botId: {}, chatId: {}", scope, layer, source, botId, chatId);
        promptNormService.restoreDefault(
                PromptScope.fromCode(scope), botId, SessionSource.fromKey(source), chatId, PromptLayer.fromCode(layer));
        return ResultUtils.success(null);
    }
}
