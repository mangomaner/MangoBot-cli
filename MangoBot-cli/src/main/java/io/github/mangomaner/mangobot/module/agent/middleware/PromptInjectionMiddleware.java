package io.github.mangomaner.mangobot.module.agent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.middleware.MiddlewareBase;
import io.github.mangomaner.mangobot.api.context.ChatContext;
import io.github.mangomaner.mangobot.module.agent.service.PromptRendererService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 三层提示词注入中间件：把 L1 框架规范 / L2 人格与行为 / L3 风格数据集追加到系统提示词末尾。
 *
 * <p>采用"追加"而非覆盖 currentPrompt，避免与 harness 内置（skill/plan 等）中间件
 * 的 onSystemPrompt 输出互相覆盖；工作区 AGENTS.md 已被 AgentWorkspaceManager 播种为
 * 空占位，内置 WorkspaceContextMiddleware 注入的是 MEMORY.md / knowledge / 工作区清单，
 * 不会再出现旧人格。任何异常都回退为原提示词，不阻断对话。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptInjectionMiddleware implements MiddlewareBase {

    private final PromptRendererService promptRendererService;

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext runtimeContext, String currentPrompt) {
        ChatContext chatContext = runtimeContext.get(ChatContext.class);
        if (chatContext == null || chatContext.getSource() == null) {
            return Mono.just(currentPrompt);
        }
        try {
            String rendered = promptRendererService.render(
                    chatContext.getBotId(), chatContext.getSource(), chatContext.getChatId());
            if (rendered == null || rendered.isBlank()) {
                return Mono.just(currentPrompt);
            }
            return Mono.just(currentPrompt + "\n" + rendered);
        } catch (Exception e) {
            log.error("Failed to inject three-layer prompt, fallback to original system prompt", e);
            return Mono.just(currentPrompt);
        }
    }
}
