package io.github.mangomaner.mangobot.module.agent.controller;

import io.github.mangomaner.mangobot.module.agent.model.dto.StyleInjectionRequest;
import io.github.mangomaner.mangobot.module.agent.model.vo.StyleInjectionVO;
import io.github.mangomaner.mangobot.module.agent.service.StyleInjectionService;
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
 * 风格数据集注入（L3 关联）控制器：作用域注入的查询、添加与移除。
 */
@Slf4j
@RestController
@RequestMapping("/api/style/injection")
@RequiredArgsConstructor
public class StyleInjectionController {

    private final StyleInjectionService styleInjectionService;

    @GetMapping("/effective")
    @Operation(summary = "当前生效注入列表", description = "按继承链解析当前实际生效的风格数据集集合")
    public BaseResponse<StyleInjectionVO> getEffective(
            @Parameter(description = "Bot ID", required = true) @RequestParam String botId,
            @Parameter(description = "来源：group / private", required = true) @RequestParam String source,
            @Parameter(description = "群聊/私聊 ID（BOT/GLOBAL 作用域可不传）") @RequestParam(required = false) String chatId) {
        return ResultUtils.success(styleInjectionService.getEffective(botId, source, chatId));
    }

    @PostMapping("/add")
    @Operation(summary = "添加注入", description = "在指定作用域追加注入一个数据集（幂等，重复添加忽略）")
    public BaseResponse<StyleInjectionVO> addInjection(@Valid @RequestBody StyleInjectionRequest request) {
        log.info("添加风格数据集注入，scope: {}, source: {}, datasetId: {}", request.getScope(), request.getSource(), request.getDatasetId());
        return ResultUtils.success(styleInjectionService.addInjection(request));
    }

    @DeleteMapping("/remove")
    @Operation(summary = "移除注入", description = "删除指定作用域下的某个数据集注入，恢复继承/不注入")
    public BaseResponse<StyleInjectionVO> removeInjection(
            @Parameter(description = "作用域：GLOBAL / BOT / SESSION", required = true) @RequestParam String scope,
            @Parameter(description = "Bot ID（非 GLOBAL 时必填）") @RequestParam(required = false) String botId,
            @Parameter(description = "来源：group / private", required = true) @RequestParam String source,
            @Parameter(description = "群聊/私聊 ID（仅 SESSION 时必填）") @RequestParam(required = false) String chatId,
            @Parameter(description = "要移除的数据集 ID", required = true) @RequestParam Long datasetId) {
        StyleInjectionRequest request = new StyleInjectionRequest();
        request.setScope(scope);
        request.setBotId(botId);
        request.setSource(source);
        request.setChatId(chatId);
        request.setDatasetId(datasetId);
        return ResultUtils.success(styleInjectionService.removeInjection(request));
    }
}
