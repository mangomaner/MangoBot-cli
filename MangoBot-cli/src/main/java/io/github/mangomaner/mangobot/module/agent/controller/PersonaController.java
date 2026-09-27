package io.github.mangomaner.mangobot.module.agent.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.mangomaner.mangobot.module.agent.capability.skill.SkillChangeNotifier;
import io.github.mangomaner.mangobot.module.agent.capability.skill.SkillManager;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentSkillConfig;
import io.github.mangomaner.mangobot.module.agent.model.dto.AssignPersonaRequest;
import io.github.mangomaner.mangobot.module.agent.model.dto.ImportSkillRequest;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.PersonaOverviewVO;
import io.github.mangomaner.mangobot.module.agent.service.AgentSkillConfigService;
import io.github.mangomaner.mangobot.system.common.BaseResponse;
import io.github.mangomaner.mangobot.system.common.ErrorCode;
import io.github.mangomaner.mangobot.system.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 人格管理控制器。
 *
 * <p>人格 skill 是一类特殊的 skill（数据库里 {@code is_persona = 1}），由人格页导入；只在「人格」页展示，
 * 不出现在「技能」页。它与普通 skill 共享同一套文件编辑端点（{@code /api/agent/capability/skills/{id}/files/*}），
 * 这里只负责：列出人格与场景指派、按场景指派/取消（场景互斥）、导入、删除。
 *
 * <p>文件内容/文件树编辑不在这里重复：人格页与技能页共用 {@code AgentCapabilityController} 的文件树端点，
 * 页面按 skill id 调用即可，避免两套长得一样的文件操作横跨两个控制器。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/capability/persona")
@Tag(name = "人格管理")
@RequiredArgsConstructor
public class PersonaController {

    private final AgentSkillConfigService skillConfigService;
    private final SkillManager skillManager;
    private final SkillChangeNotifier skillChangeNotifier;

    /**
     * 一次取回：全部人格 + 每个场景当前指派的那个人格。
     */
    @GetMapping
    @Operation(summary = "获取人格列表与场景指派")
    public BaseResponse<PersonaOverviewVO> overview() {
        // 用显式循环而非 Collectors.toMap：没指派时 activePersonaPathFor 返回 null，
        // 而 toMap 的合并分支调 Map.merge 不接受 null 值，会抛 NPE
        Map<String, String> assignment = new LinkedHashMap<>();
        for (SessionSource source : SessionSource.values()) {
            assignment.put(source.getSourceKey(), skillManager.activePersonaPathFor(source));
        }
        return new BaseResponse<>(0,
                PersonaOverviewVO.of(skillConfigService.listPersonas(), assignment), "");
    }

    /**
     * 指派（或取消）某场景的人格。新目标挂上、旧目标自动摘掉——一个场景至多一个。
     */
    @PutMapping("/assign")
    @Operation(summary = "指派/取消场景人格", description = "personaPath 传 null 表示取消该场景的人格")
    public BaseResponse<Boolean> assign(@RequestBody @Valid AssignPersonaRequest request) {
        SessionSource source;
        try {
            source = SessionSource.fromKey(request.getSource());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未知来源: " + request.getSource());
        }
        try {
            skillManager.assignPersonaSource(request.getPersonaPath(), source);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "人格指派序列化失败");
        }
        // 只有该场景的整体会话需要知道"我的人格换了"，其他场景不受影响
        skillChangeNotifier.personaChanged(source, request.getPersonaPath());
        return new BaseResponse<>(0, true, "");
    }

    @PostMapping("/import-zip")
    @Operation(summary = "导入人格（ZIP）", description = "同技能 ZIP 导入，但标记为 is_persona=1")
    public BaseResponse<AgentSkillConfig> importZip(
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请上传 ZIP 文件");
        }
        AgentSkillConfig config = skillManager.importFromZip(file.getInputStream(), true);
        skillChangeNotifier.contentChanged(config.getSkillPath());
        return new BaseResponse<>(0, config, "");
    }

    @PostMapping("/import-markdown")
    @Operation(summary = "导入人格（单文件 SKILL.md）", description = "同技能单文件导入，但标记为 is_persona=1")
    public BaseResponse<AgentSkillConfig> importMarkdown(
            @RequestBody @Valid ImportSkillRequest request) throws IOException {
        AgentSkillConfig config = skillManager.importFromMarkdown(
                request.getSkillName(), request.getDescription(), request.getContent(), true);
        skillChangeNotifier.contentChanged(config.getSkillPath());
        return new BaseResponse<>(0, config, "");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除人格")
    public BaseResponse<Boolean> delete(@PathVariable Integer id) throws IOException {
        AgentSkillConfig config = skillConfigService.getById(id);
        if (config == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR);
        }
        // 删除后该人格在 enabled_list 里的场景指派随之消失，读过它的会话由通知出口作废
        skillManager.deleteSkillDirectory(config.getSkillPath());
        skillConfigService.removeById(id);
        skillChangeNotifier.removed(config.getSkillPath());
        return new BaseResponse<>(0, true, "");
    }
}