package io.github.mangomaner.mangobot.module.agent.controller;

import io.github.mangomaner.mangobot.module.agent.model.dto.CreateChatSessionRequest;
import io.github.mangomaner.mangobot.module.agent.model.dto.UpdateChatSessionRequest;
import io.github.mangomaner.mangobot.module.agent.model.vo.ChatSessionVO;
import io.github.mangomaner.mangobot.module.agent.service.ChatSessionService;
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
 * 对话会话管理控制器
 * <p>
 * 提供对话会话的CRUD操作接口
 */
@Slf4j
@RestController
@RequestMapping("/api/chat-session")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    /**
     * 创建对话会话
     */
    @PostMapping("/create")
    @Operation(summary = "创建对话会话", description = "创建新的对话会话")
    public BaseResponse<ChatSessionVO> createSession(@Valid @RequestBody CreateChatSessionRequest request) {
        log.info("创建对话会话，title: {}", request.getTitle());
        ChatSessionVO session = chatSessionService.createSession(request);
        return ResultUtils.success(session);
    }

    /**
     * 根据ID获取会话详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取会话详情", description = "根据会话ID获取详细信息")
    public BaseResponse<ChatSessionVO> getSessionById(
            @Parameter(description = "会话ID", required = true) @PathVariable Integer id) {
        log.info("获取会话详情，sessionId: {}", id);
        ChatSessionVO session = chatSessionService.getSessionById(id);
        return ResultUtils.success(session);
    }

    /**
     * 获取指定 Bot 下的所有会话列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取会话列表", description = "获取指定 Bot 下的所有活跃会话")
    public BaseResponse<List<ChatSessionVO>> listSessionsByBotId(
            @Parameter(description = "Bot ID", required = true) @RequestParam String botId) {
        log.info("获取会话列表，botId: {}", botId);
        List<ChatSessionVO> sessions = chatSessionService.listSessionsByBotId(botId);
        return ResultUtils.success(sessions);
    }

    /**
     * 更新会话信息
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新会话", description = "更新会话标题或状态")
    public BaseResponse<ChatSessionVO> updateSession(
            @Parameter(description = "会话ID", required = true) @PathVariable Integer id,
            @Valid @RequestBody UpdateChatSessionRequest request) {
        log.info("更新会话，sessionId: {}", id);
        ChatSessionVO session = chatSessionService.updateSession(id, request);
        return ResultUtils.success(session);
    }

    /**
     * 删除会话及其所有消息
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除会话", description = "删除会话及其所有关联消息")
    public BaseResponse<Void> deleteSession(
            @Parameter(description = "会话ID", required = true) @PathVariable Integer id) {
        log.info("删除会话，sessionId: {}", id);
        chatSessionService.deleteSession(id);
        return ResultUtils.success(null);
    }
}
