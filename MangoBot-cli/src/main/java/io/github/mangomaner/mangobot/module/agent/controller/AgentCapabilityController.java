package io.github.mangomaner.mangobot.module.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.module.agent.capability.skill.SkillManager;
import io.github.mangomaner.mangobot.module.agent.capability.tool.JavaToolLoader;
import io.github.mangomaner.mangobot.module.agent.capability.tool.JavaToolMcpServer;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentJavaToolConfig;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentMcpConfig;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentMcpToolConfig;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentSkillConfig;
import io.github.mangomaner.mangobot.module.agent.model.dto.CreateMcpConfigRequest;
import io.github.mangomaner.mangobot.module.agent.model.dto.CreateSkillRequest;
import io.github.mangomaner.mangobot.module.agent.model.dto.ImportSkillRequest;
import io.github.mangomaner.mangobot.module.agent.model.dto.UpdateEnabledListRequest;
import io.github.mangomaner.mangobot.module.agent.model.dto.UpdateMcpConfigRequest;
import io.github.mangomaner.mangobot.module.agent.model.dto.UpdateMcpToolsEnabledListRequest;
import io.github.mangomaner.mangobot.module.agent.model.dto.UpdateSkillRequest;
import io.github.mangomaner.mangobot.module.agent.model.vo.JavaToolVO;
import io.github.mangomaner.mangobot.module.agent.model.vo.McpConfigVO;
import io.github.mangomaner.mangobot.module.agent.model.vo.McpToolVO;
import io.github.mangomaner.mangobot.module.agent.model.vo.SkillVO;
import io.github.mangomaner.mangobot.module.agent.service.AgentJavaToolConfigService;
import io.github.mangomaner.mangobot.module.agent.service.AgentMcpConfigService;
import io.github.mangomaner.mangobot.module.agent.service.AgentMcpToolConfigService;
import io.github.mangomaner.mangobot.module.agent.service.AgentSkillConfigService;
import io.github.mangomaner.mangobot.system.common.BaseResponse;
import io.github.mangomaner.mangobot.system.common.ErrorCode;
import io.github.mangomaner.mangobot.system.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 能力管理控制器
 *
 * <p>提供以下能力的管理接口：
 * <ul>
 *   <li>Java 工具 - 注册、启用/禁用、注销</li>
 *   <li>MCP 连接 - 创建、启用/禁用、删除</li>
 *   <li>Skill - 创建、编辑、启用/禁用、删除</li>
 * </ul>
 *
 * <p>这里只管配置的持久化：三样能力都是 spawn claude 子进程时才读取的参数，本层不做任何连接或加载。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/capability")
@Tag(name = "Agent 能力管理")
@RequiredArgsConstructor
public class AgentCapabilityController {

    private final AgentJavaToolConfigService javaToolConfigService;
    private final AgentMcpConfigService mcpConfigService;
    private final AgentMcpToolConfigService mcpToolConfigService;
    private final AgentSkillConfigService skillConfigService;

    private final JavaToolLoader javaToolLoader;
    private final JavaToolMcpServer javaToolMcpServer;
    private final SkillManager skillManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== Java 工具 ====================

    @GetMapping("/java-tools")
    @Operation(summary = "获取 Java 工具列表")
    public BaseResponse<List<JavaToolVO>> listJavaTools() {
        List<JavaToolVO> result = javaToolConfigService.list().stream()
            .map(config -> JavaToolVO.from(config, javaToolLoader))
            .collect(Collectors.toList());
        return new BaseResponse<>(0, result, "");
    }

    @PutMapping("/java-tools/{id}/toggle")
    @Operation(summary = "启用/禁用 Java 工具")
    public BaseResponse<Boolean> toggleJavaTool(@PathVariable Integer id) {
        AgentJavaToolConfig config = javaToolConfigService.getById(id);
        if (config == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR);
        }
        boolean newEnabled = !Boolean.TRUE.equals(config.getEnabled());
        config.setEnabled(newEnabled);
        javaToolConfigService.updateById(config);
        // 立即重挂 MCP tool；已跑着的 claude 会话仍用连接时的工具快照，下次 spawn 才看得到
        javaToolMcpServer.reload();
        return new BaseResponse<>(0, newEnabled, "");
    }

    @DeleteMapping("/java-tools/{id}")
    @Operation(summary = "注销 Java 工具")
    public BaseResponse<Boolean> unregisterJavaTool(@PathVariable Integer id) {
        AgentJavaToolConfig config = javaToolConfigService.getById(id);
        if (config == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR);
        }
        javaToolConfigService.removeById(id);
        javaToolMcpServer.reload();
        return new BaseResponse<>(0, true, "");
    }

    @PutMapping("/java-tools/{id}/enabled-list")
    @Operation(summary = "更新 Java 工具启用来源列表")
    public BaseResponse<Boolean> updateJavaToolEnabledList(
            @PathVariable Integer id,
            @RequestBody UpdateEnabledListRequest request) {
        try {
            javaToolConfigService.updateEnabledList(id, request.getEnabledList());
            return new BaseResponse<>(0, true, "");
        } catch (IllegalArgumentException e) {
            return new BaseResponse<>(ErrorCode.PARAMS_ERROR.getCode(), false, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to update enabled list for Java tool: {}", id, e);
            return new BaseResponse<>(ErrorCode.OPERATION_ERROR.getCode(), false, e.getMessage());
        }
    }

    // ==================== MCP 连接 ====================

    @GetMapping("/mcp")
    @Operation(summary = "获取 MCP 连接列表")
    public BaseResponse<List<McpConfigVO>> listMcpConnections() {
        List<McpConfigVO> result = mcpConfigService.list().stream()
            .map(McpConfigVO::from)
            .collect(Collectors.toList());
        return new BaseResponse<>(0, result, "");
    }

    @PostMapping("/mcp")
    @Operation(summary = "创建 MCP 连接")
    public BaseResponse<AgentMcpConfig> createMcpConnection(@RequestBody CreateMcpConfigRequest request) {
        AgentMcpConfig config = new AgentMcpConfig();
        config.setMcpName(request.getMcpName());
        config.setTransportType(request.getTransportType());
        config.setConnectionConfig(request.getConnectionConfig());
        config.setEnabled(false);
        config.setConnectionStatus(0);
        mcpConfigService.save(config);
        return new BaseResponse<>(0, config, "");
    }

    @PutMapping("/mcp/{id}/toggle")
    @Operation(summary = "启用/禁用 MCP 连接")
    public BaseResponse<Boolean> toggleMcp(@PathVariable Integer id) {
        AgentMcpConfig config = mcpConfigService.getById(id);
        if (config == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR);
        }
        boolean newEnabled = !Boolean.TRUE.equals(config.getEnabled());
        config.setEnabled(newEnabled);
        mcpConfigService.updateById(config);
        return new BaseResponse<>(0, newEnabled, "");
    }

    @DeleteMapping("/mcp/{id}")
    @Operation(summary = "删除 MCP 连接")
    public BaseResponse<Boolean> deleteMcp(@PathVariable Integer id) {
        mcpConfigService.removeById(id);
        return new BaseResponse<>(0, true, "");
    }

    @PutMapping("/mcp/{id}")
    @Operation(summary = "更新 MCP 连接配置")
    public BaseResponse<Boolean> updateMcpConnection(
            @PathVariable Integer id,
            @RequestBody UpdateMcpConfigRequest request) {
        AgentMcpConfig config = mcpConfigService.getById(id);
        if (config == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR);
        }
        
        if (Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无法修改已启用的 MCP 连接，请先禁用");
        }
        
        if (request.getMcpName() != null) {
            config.setMcpName(request.getMcpName());
        }
        if (request.getTransportType() != null) {
            config.setTransportType(request.getTransportType());
        }
        if (request.getConnectionConfig() != null) {
            config.setConnectionConfig(request.getConnectionConfig());
        }
        mcpConfigService.updateById(config);
        return new BaseResponse<>(0, true, "");
    }

    @PutMapping("/mcp/{mcpConfigId}/tools/enabled-list")
    @Operation(summary = "批量更新 MCP 连接下所有工具的启用来源列表")
    public BaseResponse<Boolean> updateMcpToolsEnabledList(
            @PathVariable Integer mcpConfigId,
            @RequestBody UpdateMcpToolsEnabledListRequest request) {
        try {
            mcpToolConfigService.updateEnabledListByMcpConfigId(mcpConfigId, request.getEnabledList());
            return new BaseResponse<>(0, true, "");
        } catch (IllegalArgumentException e) {
            return new BaseResponse<>(ErrorCode.PARAMS_ERROR.getCode(), false, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to update enabled list for MCP tools: {}", mcpConfigId, e);
            return new BaseResponse<>(ErrorCode.OPERATION_ERROR.getCode(), false, e.getMessage());
        }
    }

    @GetMapping("/mcp/{mcpConfigId}/tools")
    @Operation(summary = "获取 MCP 下的工具列表")
    public BaseResponse<List<McpToolVO>> listMcpTools(@PathVariable Integer mcpConfigId) {
        List<McpToolVO> result = mcpToolConfigService.listByMcpConfigId(mcpConfigId).stream()
            .map(McpToolVO::from)
            .collect(Collectors.toList());
        return new BaseResponse<>(0, result, "");
    }

    @PutMapping("/mcp-tools/{id}/toggle")
    @Operation(summary = "启用/禁用 MCP 工具")
    public BaseResponse<Boolean> toggleMcpTool(@PathVariable Integer id) {
        AgentMcpToolConfig config = mcpToolConfigService.getById(id);
        if (config == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR);
        }
        boolean newEnabled = !Boolean.TRUE.equals(config.getEnabled());
        config.setEnabled(newEnabled);
        mcpToolConfigService.updateById(config);
        return new BaseResponse<>(0, newEnabled, "");
    }

    @PutMapping("/mcp-tools/{id}/enabled-list")
    @Operation(summary = "更新 MCP 工具启用来源列表")
    public BaseResponse<Boolean> updateMcpToolEnabledList(
            @PathVariable Integer id,
            @RequestBody UpdateEnabledListRequest request) {
        try {
            mcpToolConfigService.updateEnabledList(id, request.getEnabledList());
            return new BaseResponse<>(0, true, "");
        } catch (IllegalArgumentException e) {
            return new BaseResponse<>(ErrorCode.PARAMS_ERROR.getCode(), false, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to update enabled list for MCP tool: {}", id, e);
            return new BaseResponse<>(ErrorCode.OPERATION_ERROR.getCode(), false, e.getMessage());
        }
    }

    // ==================== Skill ====================

    @GetMapping("/skills")
    @Operation(summary = "获取 Skill 列表")
    public BaseResponse<List<SkillVO>> listSkills() {
        List<SkillVO> result = skillConfigService.list().stream()
            .map(SkillVO::from)
            .collect(Collectors.toList());
        return new BaseResponse<>(0, result, "");
    }

    @PostMapping("/skills")
    @Operation(summary = "创建 Skill")
    public BaseResponse<AgentSkillConfig> createSkill(@RequestBody CreateSkillRequest request) throws IOException {
        String skillPath = StringUtils.hasText(request.getSkillPath())
                ? request.getSkillPath().trim()
                : SkillManager.sanitizePath(request.getSkillName());
        if (!StringUtils.hasText(skillPath)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "技能名称不能为空");
        }
        skillManager.createSkillDirectory(skillPath);
        
        String content = buildSkillContent(request);
        skillManager.writeSkillContent(skillPath, content);
        
        AgentSkillConfig config = new AgentSkillConfig();
        config.setSkillName(request.getSkillName());
        config.setDescription(request.getDescription());
        config.setSkillPath(skillPath);
        if (request.getBoundToolIds() != null && !request.getBoundToolIds().isEmpty()) {
            config.setBoundToolIds(objectMapper.writeValueAsString(request.getBoundToolIds()));
        }
        config.setEnabled(false);
        skillConfigService.save(config);
        skillManager.refreshSourcePluginDirs();

        return new BaseResponse<>(0, config, "");
    }

    @PutMapping("/skills/{id}")
    @Operation(summary = "更新 Skill")
    public BaseResponse<Boolean> updateSkill(@PathVariable Integer id, @RequestBody UpdateSkillRequest request) throws IOException {
        AgentSkillConfig config = skillConfigService.getById(id);
        if (config == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR);
        }
        
        config.setSkillName(request.getSkillName());
        config.setDescription(request.getDescription());
        if (request.getBoundToolIds() != null) {
            config.setBoundToolIds(objectMapper.writeValueAsString(request.getBoundToolIds()));
        }
        skillConfigService.updateById(config);
        
        if (request.getSkillContent() != null) {
            skillManager.writeSkillContent(config.getSkillPath(), request.getSkillContent());
        }
        skillManager.refreshSourcePluginDirs();

        return new BaseResponse<>(0, true, "");
    }

    @PutMapping("/skills/{id}/toggle")
    @Operation(summary = "启用/禁用 Skill")
    public BaseResponse<Boolean> toggleSkill(@PathVariable Integer id) {
        AgentSkillConfig config = skillConfigService.getById(id);
        if (config == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR);
        }
        boolean newEnabled = !Boolean.TRUE.equals(config.getEnabled());
        config.setEnabled(newEnabled);
        skillConfigService.updateById(config);
        skillManager.refreshSourcePluginDirs();
        return new BaseResponse<>(0, newEnabled, "");
    }

    @PutMapping("/skills/{id}/enabled-list")
    @Operation(summary = "更新 Skill 启用来源列表")
    public BaseResponse<Boolean> updateSkillEnabledList(
            @PathVariable Integer id,
            @RequestBody UpdateEnabledListRequest request) {
        try {
            skillConfigService.updateEnabledList(id, request.getEnabledList());
            skillManager.refreshSourcePluginDirs();
            return new BaseResponse<>(0, true, "");
        } catch (IllegalArgumentException e) {
            return new BaseResponse<>(ErrorCode.PARAMS_ERROR.getCode(), false, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to update enabled list for skill: {}", id, e);
            return new BaseResponse<>(ErrorCode.OPERATION_ERROR.getCode(), false, e.getMessage());
        }
    }

    @DeleteMapping("/skills/{id}")
    @Operation(summary = "删除 Skill")
    public BaseResponse<Boolean> deleteSkill(@PathVariable Integer id) throws IOException {
        AgentSkillConfig config = skillConfigService.getById(id);
        if (config == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR);
        }
        skillManager.deleteSkillDirectory(config.getSkillPath());
        skillConfigService.removeById(id);
        skillManager.refreshSourcePluginDirs();
        return new BaseResponse<>(0, true, "");
    }

    @GetMapping("/skills/{id}/content")
    @Operation(summary = "获取 Skill 文件内容")
    public BaseResponse<String> getSkillContent(@PathVariable Integer id) throws IOException {
        AgentSkillConfig config = skillConfigService.getById(id);
        if (config == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR);
        }
        String content = skillManager.readSkillContent(config.getSkillPath());
        return new BaseResponse<>(0, content, "");
    }

    @PostMapping("/skills/import-zip")
    @Operation(summary = "导入 Skill（ZIP）", description = "上传包含 SKILL.md 的 ZIP，可附带 references/examples/scripts 资源")
    public BaseResponse<AgentSkillConfig> importSkillZip(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请上传 ZIP 文件");
        }
        AgentSkillConfig config = skillManager.importFromZip(file.getInputStream());
        skillManager.refreshSourcePluginDirs();
        return new BaseResponse<>(0, config, "");
    }

    @PostMapping("/skills/import-markdown")
    @Operation(summary = "导入 Skill（单文件 SKILL.md）", description = "直接粘贴 SKILL.md 内容创建技能")
    public BaseResponse<AgentSkillConfig> importSkillMarkdown(
            @RequestBody @Valid ImportSkillRequest request) throws IOException {
        AgentSkillConfig config = skillManager.importFromMarkdown(
                request.getSkillName(), request.getDescription(), request.getContent());
        skillManager.refreshSourcePluginDirs();
        return new BaseResponse<>(0, config, "");
    }

    private String buildSkillContent(CreateSkillRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(request.getSkillName()).append("\n");
        sb.append("description: ").append(request.getDescription() != null ? request.getDescription() : "").append("\n");
        if (request.getBoundToolIds() != null && !request.getBoundToolIds().isEmpty()) {
            sb.append("bound_tool_ids: ").append(request.getBoundToolIds()).append("\n");
        }
        sb.append("---\n\n");
        if (request.getSkillContent() != null) {
            sb.append(request.getSkillContent());
        }
        return sb.toString();
    }
}
