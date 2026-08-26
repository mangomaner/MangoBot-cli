package io.github.mangomaner.mangobot.module.agent.controller;

import io.github.mangomaner.mangobot.module.agent.model.dto.ChatMessageWebRequest;
import io.github.mangomaner.mangobot.module.agent.model.vo.ChatMessageWebVO;
import io.github.mangomaner.mangobot.module.agent.service.ChatMessageWebService;
import io.github.mangomaner.mangobot.system.common.BaseResponse;
import io.github.mangomaner.mangobot.system.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对话消息管理控制器
 * <p>
 * 提供对话消息的创建、查询、删除等操作接口
 */
@Slf4j
@RestController
@RequestMapping("/api/chat-message")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageWebService chatMessageService;

    /**
     * 创建对话消息
     */
    @PostMapping("/create")
    @Operation(summary = "创建消息", description = "在指定会话中创建新消息")
    public BaseResponse<ChatMessageWebVO> createMessage(@Valid @RequestBody ChatMessageWebRequest request) {
        log.info("创建消息，sessionId: {}, role: {}", request.getSessionId(), request.getRole());
        ChatMessageWebVO message = chatMessageService.createMessage(request);
        return ResultUtils.success(message);
    }

    /**
     * 根据ID获取消息
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取消息详情", description = "根据消息ID获取详细信息")
    public BaseResponse<ChatMessageWebVO> getMessageById(
            @Parameter(description = "消息ID", required = true) @PathVariable Integer id) {
        log.info("获取消息详情，messageId: {}", id);
        ChatMessageWebVO message = chatMessageService.getMessageById(id);
        return ResultUtils.success(message);
    }

    /**
     * 获取会话下的所有消息列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取消息列表", description = "按 id 正序分页返回指定会话的消息；beforeId 为空取最近一页，非空取该 id 之前的更早一页")
    public BaseResponse<List<ChatMessageWebVO>> listMessagesBySessionId(
            @Parameter(description = "会话ID", required = true) @RequestParam Integer sessionId,
            @Parameter(description = "翻页游标：取更早消息时传当前最旧消息 id") @RequestParam(required = false) Integer beforeId,
            @Parameter(description = "每页条数，默认30") @RequestParam(required = false, defaultValue = "30") Integer limit) {
        log.info("获取消息列表，sessionId: {}, beforeId: {}, limit: {}", sessionId, beforeId, limit);
        List<ChatMessageWebVO> messages = chatMessageService.listMessagesBySessionId(sessionId, beforeId, limit);
        return ResultUtils.success(messages);
    }

    /**
     * 获取会话下指定角色的消息列表
     */
    @GetMapping("/list-by-role")
    @Operation(summary = "按角色获取消息", description = "获取指定会话下特定角色的消息列表")
    public BaseResponse<List<ChatMessageWebVO>> listMessagesBySessionIdAndRole(
            @Parameter(description = "会话ID", required = true) @RequestParam Integer sessionId,
            @Parameter(description = "角色(user/assistant/system)", required = true) @RequestParam String role) {
        log.info("按角色获取消息，sessionId: {}, role: {}", sessionId, role);
        List<ChatMessageWebVO> messages = chatMessageService.listMessagesBySessionIdAndRole(sessionId, role);
        return ResultUtils.success(messages);
    }

    /**
     * 删除单条消息
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除消息", description = "删除指定的单条消息")
    public BaseResponse<Void> deleteMessage(
            @Parameter(description = "消息ID", required = true) @PathVariable Integer id) {
        log.info("删除消息，messageId: {}", id);
        chatMessageService.deleteMessage(id);
        return ResultUtils.success(null);
    }
}
