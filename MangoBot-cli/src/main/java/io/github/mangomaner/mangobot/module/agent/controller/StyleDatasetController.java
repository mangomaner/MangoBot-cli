package io.github.mangomaner.mangobot.module.agent.controller;

import io.github.mangomaner.mangobot.module.agent.model.dto.StyleDatasetImportRequest;
import io.github.mangomaner.mangobot.module.agent.model.dto.StyleDatasetRequest;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.StyleDatasetDetailVO;
import io.github.mangomaner.mangobot.module.agent.model.vo.StyleDatasetVO;
import io.github.mangomaner.mangobot.module.agent.service.StyleDatasetService;
import io.github.mangomaner.mangobot.system.common.BaseResponse;
import io.github.mangomaner.mangobot.system.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 风格数据集（L3）数据中心控制器：CRUD 与从会话导入。
 */
@Slf4j
@RestController
@RequestMapping("/api/style/dataset")
@RequiredArgsConstructor
public class StyleDatasetController {

    private final StyleDatasetService styleDatasetService;

    @GetMapping("/list")
    @Operation(summary = "数据集列表", description = "按来源列出公共风格数据集（不含明细）")
    public BaseResponse<List<StyleDatasetVO>> listBySource(
            @Parameter(description = "来源：group / private", required = true) @RequestParam String source) {
        return ResultUtils.success(styleDatasetService.listBySource(SessionSource.fromKey(source)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "数据集详情", description = "含角色与消息明细")
    public BaseResponse<StyleDatasetDetailVO> getDetail(
            @Parameter(description = "数据集 ID", required = true) @PathVariable Long id) {
        return ResultUtils.success(styleDatasetService.getDetail(id));
    }

    @PostMapping("/create")
    @Operation(summary = "创建数据集", description = "人工创建风格数据集")
    public BaseResponse<StyleDatasetDetailVO> create(@Valid @RequestBody StyleDatasetRequest request) {
        return ResultUtils.success(styleDatasetService.create(request));
    }

    @PutMapping("/update")
    @Operation(summary = "编辑数据集", description = "编辑名称/描述/角色/消息（需携带 id）")
    public BaseResponse<StyleDatasetDetailVO> update(@Valid @RequestBody StyleDatasetRequest request) {
        return ResultUtils.success(styleDatasetService.update(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除数据集", description = "级联清理引用该数据集的注入关联")
    public BaseResponse<Void> delete(
            @Parameter(description = "数据集 ID", required = true) @PathVariable Long id) {
        styleDatasetService.delete(id);
        return ResultUtils.success(null);
    }

    @PostMapping("/import")
    @Operation(summary = "从会话导入", description = "按选中消息的发送者自动归并生成角色")
    public BaseResponse<StyleDatasetDetailVO> importFromSession(@Valid @RequestBody StyleDatasetImportRequest request) {
        return ResultUtils.success(styleDatasetService.importFromSession(request));
    }
}
