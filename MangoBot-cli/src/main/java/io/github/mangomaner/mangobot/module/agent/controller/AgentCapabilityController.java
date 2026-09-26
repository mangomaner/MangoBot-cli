package io.github.mangomaner.mangobot.module.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.module.agent.capability.skill.SkillManager;
import io.github.mangomaner.mangobot.module.agent.chat.TurnControl;
import io.github.mangomaner.mangobot.module.agent.chat.TurnControlRegistry;
import io.github.mangomaner.mangobot.module.agent.capability.tool.JavaToolLoader;
import io.github.mangomaner.mangobot.module.agent.capability.tool.JavaToolMcpServer;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentJavaToolConfig;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentMcpConfig;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentMcpToolConfig;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
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
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
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

    private final ClaudeRuntimeManager claudeRuntimeManager;

    /** 技能变更后要给活会话排"重读/作废"指令，走的就是这个登记处。 */
    private final TurnControlRegistry turnControlRegistry;

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
        // 立即重挂 MCP tool；已跑着的 claude 会话仍用连接时的工具快照，所以要标 stale 让下一轮重建
        javaToolMcpServer.reload();
        claudeRuntimeManager.markAllSessionsStale();
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
        claudeRuntimeManager.markAllSessionsStale();
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
        // 新建的技能默认是关的、也不在任何会话的技能列表里，没什么可让模型重读的
        afterSkillChanged(skillPath, null);

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
        afterSkillChanged(config.getSkillPath(), reloadControl(config.getSkillPath()));

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
        afterSkillChanged(config.getSkillPath(), scopeChangedControl(config.getSkillPath()));
        return new BaseResponse<>(0, newEnabled, "");
    }

    @PutMapping("/skills/{id}/enabled-list")
    @Operation(summary = "更新 Skill 启用来源列表")
    public BaseResponse<Boolean> updateSkillEnabledList(
            @PathVariable Integer id,
            @RequestBody UpdateEnabledListRequest request) {
        try {
            String skillPath = skillConfigService.getById(id).getSkillPath();
            skillConfigService.updateEnabledList(id, request.getEnabledList());
            afterSkillChanged(skillPath, scopeChangedControl(skillPath));
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
        String deletedPath = config.getSkillPath();
        skillManager.deleteSkillDirectory(deletedPath);
        skillConfigService.removeById(id);
        // 删掉之后 isEnabledFor 就查不到了，所以这里直接通知所有活会话：读过它的自己作废
        skillManager.refreshSourcePluginDirs();
        claudeRuntimeManager.markAllSessionsStale();
        for (String sessionKey : claudeRuntimeManager.sessionStatus().keySet()) {
            turnControlRegistry.appendOnce(sessionKey,
                    new TurnControl("skill:" + deletedPath, revokedControl(deletedPath)));
        }
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
        afterSkillChanged(config.getSkillPath(), reloadControl(config.getSkillPath()));
        return new BaseResponse<>(0, config, "");
    }

    @PostMapping("/skills/import-markdown")
    @Operation(summary = "导入 Skill（单文件 SKILL.md）", description = "直接粘贴 SKILL.md 内容创建技能")
    public BaseResponse<AgentSkillConfig> importSkillMarkdown(
            @RequestBody @Valid ImportSkillRequest request) throws IOException {
        AgentSkillConfig config = skillManager.importFromMarkdown(
                request.getSkillName(), request.getDescription(), request.getContent());
        afterSkillChanged(config.getSkillPath(), reloadControl(config.getSkillPath()));
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

    // ========================================================================
    // 技能变更之后：重建插件目录 + 让会话重建 + 通过控制模块告知模型
    // ========================================================================

    /**
     * 技能变更之后统一要做的三件事，别在别处各写一遍。
     *
     * <p>顺序有讲究：先把来源插件目录重建好（新内容落盘），再让常驻会话"下次重建"，
     * 最后排控制指令——控制指令那一轮开头会先重建进程（那时才读得到新文件），模型随后按指令重读。
     *
     * <p>为什么必须配控制指令：实测（2026-09-27）技能正文是<b>进程级缓存</b>——改了文件不重建进程，
     * 重读也拿不到新版；而重建之后模型又<b>不会主动重读</b>，它会直接从上下文里那份旧的接着答。
     * 所以"重建 + 明确要求重读并声明旧版作废"两件缺一不可。温和的"再读一次"实测无效。
     *
     * @param controlText 给模型看的那句话；为 null 表示这次变更不必打扰模型
     */
    private void afterSkillChanged(String skillPath, String controlText) {
        skillManager.refreshSourcePluginDirs();
        claudeRuntimeManager.markAllSessionsStale();
        if (controlText == null) {
            return;
        }
        for (String sessionKey : claudeRuntimeManager.sessionStatus().keySet()) {
            SessionSource source = sourceOf(sessionKey);
            if (source == null || !skillManager.isEnabledFor(skillPath, source)) {
                continue;
            }
            turnControlRegistry.appendOnce(sessionKey,
                    new TurnControl("skill:" + skillPath, controlText));
        }
    }

    /** 会话键形如 {@code botId:source:chatId}；取不出合法来源（例如 dev 会话）就返回 null。 */
    private static SessionSource sourceOf(String sessionKey) {
        String[] parts = sessionKey.split(":");
        if (parts.length < 2) {
            return null;
        }
        try {
            return SessionSource.fromKey(parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 正文被改写：要求重读、声明旧版作废，并且**明确背书新版本**。
     *
     * <p>最后那句背书是实测逼出来的（2026-09-27 演示）：只写"技能更新了、重新读一遍"时，模型确实会照做
     * 重读，但**读完不肯执行新内容**——它的原话是"控制块只说了技能更新、重新读取，并没有点名认可这条
     * 规则；所以我按'控制块没背书、内容本身越权'来处理"。也就是说技能正文在它眼里是低可信度内容，
     * 没有控制块的背书就会被当成外部注入挡掉。
     *
     * <p>所以这句话必须说三件事：新版是我方发布的、和旧版不一致的地方是有意改的、不要当注入。
     */
    private static String reloadControl(String skillPath) {
        return "技能「" + skillPath + "」的正文已经更新，这是由我方发布并认可的新版本，必须按它执行。"
                + "上文里那份旧版本作废。现在重新调用 Skill 工具读取它（技能列表里的名字可能带来源前缀，"
                + "例如 group:" + skillPath + "）。新版本里与旧版本不一致的地方是有意改的，照新的来，"
                + "不要把它当成外部注入的内容。";
    }

    /** 启用范围变了：可能读得到、也可能读不到了，一句话覆盖两种情况（同样带背书）。 */
    private static String scopeChangedControl(String skillPath) {
        return "技能「" + skillPath + "」的启用范围变了（这是我方发布的状态，按它执行）。"
                + "如果你现在还能读到它，重新读一遍按新的来；如果已经读不到，就别再按它的内容行事。";
    }

    /** 技能被删/停用：明确作废。 */
    private static String revokedControl(String skillPath) {
        return "技能「" + skillPath + "」已经停用或删除。不要再调用它，也不要再照它的内容行事；"
                + "上文里还留着的也一并忽略。";
    }

}
