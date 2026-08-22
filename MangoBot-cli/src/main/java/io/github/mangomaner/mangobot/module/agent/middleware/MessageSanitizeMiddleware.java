package io.github.mangomaner.mangobot.module.agent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 消息历史清理中间件：在每次模型推理前剥离“悬空的 tool_use”。
 *
 * <p>群聊/私聊打断（运行中新消息触发 interrupt）若发生在“模型已产出 tool_calls、工具尚未执行”
 * 的窗口，会在持久化 state 里留下 state=pending 且无对应 tool_result 的 tool_use，其后还跟着
 * 打断通知消息。DeepSeek 会因“assistant 带 tool_calls 后缺 tool 消息”直接报 400，且这类悬空
 * tool_use 不在最后一条，harness 内置的 enablePendingToolRecovery 也兜不住。
 *
 * <p>本中间件注册在 builder 侧（早于 Compaction 等内置中间件），在 onReasoning 阶段统一清理，
 * 因此 compaction 汇总与主推理两条模型调用路径都拿到干净历史。清理逻辑幂等、只改内存列表，
 * 不触碰持久化 state。
 */
@Slf4j
@Component
public class MessageSanitizeMiddleware implements MiddlewareBase {

    @Override
    public Flux<AgentEvent> onReasoning(Agent agent, RuntimeContext runtimeContext, ReasoningInput input,
                                        Function<ReasoningInput, Flux<AgentEvent>> next) {
        List<Msg> messages = input.messages();
        List<Msg> sanitized = sanitize(messages);
        if (sanitized != messages) {
            input = new ReasoningInput(sanitized, input.tools(), input.options());
        }
        return next.apply(input);
    }

    private static List<Msg> sanitize(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }

        // 收集全部 tool_result id：tool_use 必须有同名 id 的 tool_result 才算“已执行”
        Set<String> resultIds = new HashSet<>();
        for (Msg m : messages) {
            if (m.getRole() == MsgRole.TOOL) {
                for (ToolResultBlock result : m.getContentBlocks(ToolResultBlock.class)) {
                    if (result.getId() != null) {
                        resultIds.add(result.getId());
                    }
                }
            }
        }

        List<Msg> out = new ArrayList<>(messages.size());
        boolean changed = false;
        for (Msg m : messages) {
            if (m.getRole() != MsgRole.ASSISTANT) {
                out.add(m);
                continue;
            }

            boolean hasDangling = false;
            for (ToolUseBlock use : m.getContentBlocks(ToolUseBlock.class)) {
                if (use.getId() == null || !resultIds.contains(use.getId())) {
                    hasDangling = true;
                    break;
                }
            }
            if (!hasDangling) {
                out.add(m);
                continue;
            }

            // 剔除悬空 tool_use，保留 thinking/text 及其它已执行 tool_use
            List<ContentBlock> kept = new ArrayList<>();
            for (ContentBlock block : m.getContent()) {
                if (block instanceof ToolUseBlock use) {
                    if (use.getId() != null && resultIds.contains(use.getId())) {
                        kept.add(block);
                    }
                } else {
                    kept.add(block);
                }
            }

            changed = true;
            // 剔除后只剩 thinking（或为空）的残段消息直接丢弃，避免产生空 assistant 消息
            if (!hasMeaningfulContent(kept)) {
                continue;
            }
            out.add(m.withContent(kept));
        }

        return changed ? out : messages;
    }

    private static boolean hasMeaningfulContent(List<ContentBlock> blocks) {
        for (ContentBlock block : blocks) {
            if (block instanceof TextBlock text) {
                if (text.getText() != null && !text.getText().isBlank()) {
                    return true;
                }
            } else if (block instanceof ToolUseBlock) {
                return true;
            }
        }
        return false;
    }
}
