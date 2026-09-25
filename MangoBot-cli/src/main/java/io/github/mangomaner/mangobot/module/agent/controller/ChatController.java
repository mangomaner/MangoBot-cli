package io.github.mangomaner.mangobot.module.agent.controller;

import io.github.mangomaner.mangobot.module.agent.model.dto.CreateChatSessionRequest;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.ChatSessionVO;
import io.github.mangomaner.mangobot.module.agent.service.ChatSessionService;
import io.github.mangomaner.mangobot.system.common.BaseResponse;
import io.github.mangomaner.mangobot.system.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话控制器
 * <p>
 * 只负责 Web 会话的创建。流式对话接口待接入 Claude 运行时后重建。
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatSessionService chatSessionService;

    @PostMapping("/session")
    @Operation(summary = "创建Web会话", description = "创建一个新的web端对话会话")
    public BaseResponse<ChatSessionVO> createWebSession(@RequestBody CreateChatSessionRequest request) {
        request.setSource(SessionSource.WEB);
        log.info("创建Web会话，title: {}", request.getTitle());
        ChatSessionVO session = chatSessionService.createSession(request);
        return ResultUtils.success(session);
    }
}
