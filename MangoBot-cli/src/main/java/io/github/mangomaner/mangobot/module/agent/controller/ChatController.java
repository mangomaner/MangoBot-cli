package io.github.mangomaner.mangobot.module.agent.controller;

import io.github.mangomaner.mangobot.module.agent.chat.ChatStreamMapper;
import io.github.mangomaner.mangobot.module.agent.model.dto.ChatMessageWebRequest;
import io.github.mangomaner.mangobot.module.agent.model.dto.CreateChatSessionRequest;
import io.github.mangomaner.mangobot.module.agent.model.dto.StreamChatRequest;
import io.github.mangomaner.mangobot.module.agent.chat.SessionKeys;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.ChatSessionVO;
import io.github.mangomaner.mangobot.infra.claude.session.ClaudeEvent;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
import io.github.mangomaner.mangobot.infra.claude.session.ClaudeSession;
import io.github.mangomaner.mangobot.infra.claude.session.ClaudeTurnInput;
import io.github.mangomaner.mangobot.module.agent.service.ChatMessageWebService;
import io.github.mangomaner.mangobot.module.agent.service.ChatSessionService;
import io.github.mangomaner.mangobot.system.common.BaseResponse;
import io.github.mangomaner.mangobot.system.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

/**
 * 对话控制器：Web 会话的创建与流式对话。
 *
 * <p>流式接口的实现细节全部压在 {@link ChatStreamMapper} 与 claude 运行时里，这里只做四件事：
 * 写用户消息、取会话、非阻塞地发一轮、把这一轮的原文落库。
 *
 * <p><b>落库只在这里做</b>：前端拿到流之后只把内容 push 进本地列表（{@code onMessageSaved}），
 * 刷新页面靠的是后端历史。所以用户消息与 assistant 消息都必须由后端写，缺一条刷新后就会丢。
 *
 * <p>会话键是 {@code web:<sessionId>}：web 会话在库里 {@code bot_id} 为 NULL，拿不到 Bot 身份，
 * 而 {@code botId} 在真实会话里是纯数字，用字面量 {@code web} 占位不会撞上任何一个 Bot。
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    /** 流结束标记，前端靠它把这一轮收尾并落库到本地列表。 */
    private static final String DONE = "[DONE]";

    private final ChatSessionService chatSessionService;
    private final ChatMessageWebService chatMessageWebService;
    private final ClaudeRuntimeManager runtimeManager;
    private final ChatStreamMapper chatStreamMapper;

    @PostMapping("/session")
    @Operation(summary = "创建Web会话", description = "创建一个新的web端对话会话")
    public BaseResponse<ChatSessionVO> createWebSession(@RequestBody CreateChatSessionRequest request) {
        request.setSource(SessionSource.WEB);
        log.info("创建Web会话，title: {}", request.getTitle());
        ChatSessionVO session = chatSessionService.createSession(request);
        return ResultUtils.success(session);
    }

    /**
     * 流式对话：SSE 吐"正文 + 标签"的纯文本帧，末帧固定 {@code [DONE]}。
     *
     * <p>任何失败都转成一条 {@code <Error>} 帧加 {@code [DONE]} 收尾，而不是抛出去让响应变成 500：
     * 前端是靠读到 {@code [DONE]} 才把内容落进列表的（{@code useStreamHandler} 的 {@code closeStream}），
     * 抛异常它只会看到一个断掉的连接，用户那边连错误原因都看不到。
     *
     * <p>本会话已有轮次在跑时直接回一句"正在生成回复"，<b>不排队</b>——排队会让请求挂在那里，
     * 用户以为卡死了；QQ 那边才是进缓冲等下一轮。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话", description = "使用 Server-Sent Events 实现流式输出，自动持久化用户与助手消息")
    public Flux<String> streamChat(@Valid @RequestBody StreamChatRequest request) {
        Integer sessionId = request.getSessionId();
        String message = request.getMessage();
        log.info("收到 Web 流式对话请求：sessionId={} 正文长度={}", sessionId,
                message == null ? 0 : message.length());

        try {
            persistUserMessage(sessionId, message);
        } catch (Exception e) {
            log.warn("写入用户消息失败，这一轮不发起：sessionId={}", sessionId, e);
            return Flux.just(errorLine(e.getMessage()), DONE);
        }

        if (!runtimeManager.isReady()) {
            return Flux.just(errorLine("Claude 运行时未就绪：" + runtimeManager.notReadyReason()), DONE);
        }

        ClaudeSession session;
        Optional<Flux<ClaudeEvent>> turn;
        try {
            session = runtimeManager.getOrCreate(SessionKeys.WEB_BOT_ID, String.valueOf(sessionId),
                    SessionSource.WEB);
            turn = session.trySendTurn(new ClaudeTurnInput(message));
        } catch (Exception e) {
            log.error("Web 流式对话取会话失败：sessionId={}", sessionId, e);
            return Flux.just(errorLine("对话启动失败：" + e.getMessage()), DONE);
        }

        if (turn.isEmpty()) {
            log.info("会话正忙，本轮直接拒绝：sessionId={}", sessionId);
            return Flux.just(errorLine("本会话正在生成回复"), DONE);
        }

        // 边流边攒"带标签的原文"：前端历史渲染走的正是这套标签，落库要落原样
        StringBuilder transcript = new StringBuilder();
        return chatStreamMapper.map(turn.get())
                .doOnNext(frame -> transcript.append(ChatStreamMapper.toStorageText(frame)))
                .onErrorResume(e -> {
                    log.error("Web 流式对话出错：sessionId={}", sessionId, e);
                    return Flux.just(errorLine(e.getMessage()));
                })
                .concatWith(Mono.fromRunnable(() -> persistAssistantMessage(sessionId, transcript))
                        .subscribeOn(Schedulers.boundedElastic())
                        .thenReturn(DONE));
    }

    // ========================================================================
    // 落库
    // ========================================================================

    private void persistUserMessage(Integer sessionId, String message) {
        ChatMessageWebRequest userMessage = new ChatMessageWebRequest();
        userMessage.setSessionId(sessionId);
        userMessage.setContent(message);
        userMessage.setRole("user");
        chatMessageWebService.createMessage(userMessage);
    }

    /**
     * 落 assistant 回复。
     *
     * <p>空内容不落：本轮只出了错误帧或一条话都没说时，库里多一条空消息只会让历史更乱。
     * 落库失败也只告警——用户该看到的回复已经在屏幕上流完了，不该因为写库失败反过来报错。
     */
    private void persistAssistantMessage(Integer sessionId, StringBuilder transcript) {
        if (transcript.isEmpty()) {
            return;
        }
        try {
            chatMessageWebService.createAssistantMessage(sessionId, transcript.toString(), null);
        } catch (Exception e) {
            log.error("写入助手消息失败：sessionId={}", sessionId, e);
        }
    }

    private static String errorLine(String message) {
        return "<Error>" + (message == null ? "未知错误" : message) + "</Error>";
    }
}
