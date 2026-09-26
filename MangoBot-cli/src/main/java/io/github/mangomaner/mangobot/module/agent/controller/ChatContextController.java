package io.github.mangomaner.mangobot.module.agent.controller;

import io.github.mangomaner.mangobot.module.agent.chat.ChatTarget;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
import io.github.mangomaner.mangobot.system.common.BaseResponse;
import io.github.mangomaner.mangobot.system.common.ResultUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 清空某个聊天的 agent 上下文。
 *
 * <p>给群聊页/私聊页上那个「清空上下文」按钮用：bot 把某段对话记岔了、或者想让它重新开始认人时，
 * 点一下就把它的记忆清掉。
 *
 * <p><b>只清 agent 的上下文，不删聊天记录</b>——{@code group_messages} / {@code private_messages}
 * 是消息存档（页面上还在那儿），不是 agent 的记忆。清的是 claude 进程里那段对话历史。
 */
@RestController
@RequestMapping("/api/chat/context")
@RequiredArgsConstructor
@Slf4j
public class ChatContextController {

    private final ClaudeRuntimeManager runtimeManager;

    /**
     * 清空 {@code botId} 在 {@code source} 下与 {@code targetId}（群号 / 用户号）这个聊天的上下文。
     *
     * <p>幂等：本来就没在跑也返回成功——"没有上下文"这个目标状态已经达成了。
     */
    @PostMapping("/reset")
    public BaseResponse<ResetVO> reset(@RequestBody ResetRequest request) {
        SessionSource source;
        try {
            source = SessionSource.fromKey(request.source());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("来源不认识：" + request.source()
                    + "（可选 web / group / private）");
        }

        String sessionKey = new ChatTarget(request.botId(), source, request.targetId()).sessionKey();
        boolean closed = runtimeManager.resetSession(sessionKey);
        log.info("清空上下文请求：sessionKey={} 关掉了在跑的进程={}", sessionKey, closed);
        return ResultUtils.success(new ResetVO(sessionKey, closed));
    }

    public record ResetRequest(long botId, String source, long targetId) {
    }

    public record ResetVO(String sessionKey, boolean processClosed) {
    }
}
