package io.github.mangomaner.mangobot.module.agent.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.mangomaner.mangobot.module.agent.capability.skill.PersonaScope;
import io.github.mangomaner.mangobot.module.agent.capability.skill.SkillChangeNotifier;
import io.github.mangomaner.mangobot.module.agent.capability.skill.SkillLibrary;
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

/**
 * 人格管理控制器。
 *
 * <p>人格是<b>人格库</b>（{@code data/capabilities/all-personas/}）里的技能——类型由所在库决定，
 * 数据库里没有标记位，所以同一个名字可以在技能库和人格库各存一份、互不干扰。人格只在「人格」页展示，
 * 不出现在「技能」页。它与普通技能共享同一套文件编辑端点
 * （{@code /api/agent/capability/skills/{id}/files/*}），这里只负责：列出人格与指派、
 * 按作用域指派/取消（每个作用域互斥）、导入、删除。
 *
 * <p>作用域三级：全局 / bot / 会话，越具体越优先（解析在 {@code ClaudeSkillRegistry}）。
 * Web 只有全局级。
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

    /** 一次取回：全部人格 + 当前全部指派。 */
    @GetMapping
    @Operation(summary = "获取人格列表与各级指派")
    public BaseResponse<PersonaOverviewVO> overview() {
        return new BaseResponse<>(0,
                PersonaOverviewVO.of(skillConfigService.listPersonas(), skillManager.listPersonaAssignments()), "");
    }

    /**
     * 指派（或取消）某作用域的人格。新目标挂上、该作用域的旧目标自动摘掉——一个作用域至多一个。
     */
    @PutMapping("/assign")
    @Operation(summary = "指派/取消人格", description = "personaPath 传 null 表示取消该作用域的人格")
    public BaseResponse<Boolean> assign(@RequestBody @Valid AssignPersonaRequest request) {
        SessionSource source;
        try {
            source = SessionSource.fromKey(request.getSource());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未知来源: " + request.getSource());
        }
        PersonaScope scope;
        try {
            scope = new PersonaScope(source, blankToNull(request.getBotId()), blankToNull(request.getTargetId()));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, e.getMessage());
        }
        try {
            skillManager.assignPersona(request.getPersonaPath(), scope);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "人格指派序列化失败");
        }
        // 只该通知"这个作用域下的会话"：全局级是该来源全部会话，bot / 会话级逐级收窄
        skillChangeNotifier.personaChanged(scope, request.getPersonaPath());
        return new BaseResponse<>(0, true, "");
    }

    @PostMapping("/import-zip")
    @Operation(summary = "导入人格（ZIP）", description = "同技能 ZIP 导入，但落进人格库")
    public BaseResponse<AgentSkillConfig> importZip(
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请上传 ZIP 文件");
        }
        AgentSkillConfig config = skillManager.importFromZip(file.getInputStream(), SkillLibrary.Kind.PERSONA);
        skillChangeNotifier.contentChanged(config.getSkillPath());
        return new BaseResponse<>(0, config, "");
    }

    @PostMapping("/import-markdown")
    @Operation(summary = "导入人格（单文件 SKILL.md）", description = "同技能单文件导入，但落进人格库")
    public BaseResponse<AgentSkillConfig> importMarkdown(
            @RequestBody @Valid ImportSkillRequest request) throws IOException {
        AgentSkillConfig config = skillManager.importFromMarkdown(
                request.getSkillName(), request.getDescription(), request.getContent(), SkillLibrary.Kind.PERSONA);
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
        // 删除后该人格在 enabled_list 里的指派随之消失，读过它的会话由通知出口作废
        skillManager.deleteSkillDirectory(config.getSkillPath());
        skillConfigService.removeById(id);
        skillChangeNotifier.removed(config.getSkillPath());
        return new BaseResponse<>(0, true, "");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
