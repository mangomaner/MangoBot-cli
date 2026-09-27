package io.github.mangomaner.mangobot.web_controller.claude_smoke;

import io.github.mangomaner.mangobot.infra.claude.session.ClaudeEvent;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
import io.github.mangomaner.mangobot.infra.claude.session.ClaudeSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 接入管道的验证出口：把一轮 claude 对话的原始事件流按 SSE 打出来。
 *
 * <p>这一个端点同时证明三件事：
 * <ul>
 *   <li><b>能拉起并驱动</b>——有事件回来；</li>
 *   <li><b>能流式</b>——每行前缀的 {@code +Nms} 递增，而不是最后一秒全吐出来；</li>
 *   <li><b>能拿到完整内容</b>——收尾的 {@code <ClaudeSmoke>} 标签里 {@code assistantChars} &gt; 0
 *       且 {@code complete="true"}，标签体内是拼接后的正文。</li>
 * </ul>
 *
 * <p>收尾信息刻意做成单行属性标签，方便脚本用正则直接断言，不必解析正文。
 */
@RestController
@RequestMapping("/api/claude")
@Slf4j
public class ClaudeSmokeController {

    private static final int PREVIEW_CHARS = 80;

    private final ClaudeRuntimeManager runtimeManager;

    public ClaudeSmokeController(ClaudeRuntimeManager runtimeManager) {
        this.runtimeManager = runtimeManager;
    }

    @GetMapping(value = "/smoke", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> smoke(
            @RequestParam String message,
            @RequestParam(defaultValue = "smoke") String botId,
            @RequestParam(defaultValue = "default") String sourceKey) {

        if (!runtimeManager.isReady()) {
            return Flux.just("runtime not ready: " + runtimeManager.notReadyReason(),
                    footer(false, "", "", 0, 0, 0),
                    "[DONE]");
        }

        ClaudeSession session;
        try {
            session = runtimeManager.getOrCreate(botId, sourceKey);
        } catch (Exception e) {
            log.error("Failed to obtain claude session for smoke test", e);
            return Flux.just("failed to start claude session: " + e.getMessage(),
                    footer(false, "", "", 0, 0, 0),
                    "[DONE]");
        }

        long startMillis = System.currentTimeMillis();
        AtomicLong lastMillis = new AtomicLong(startMillis);
        AtomicReference<ClaudeEvent> terminal = new AtomicReference<>();
        StringBuilder assistantText = new StringBuilder();

        Flux<String> eventLines = session.sendTurn(message)
                .map(event -> {
                    long now = System.currentTimeMillis();
                    long delta = now - lastMillis.getAndSet(now);
                    if (event.isAssistant()) {
                        assistantText.append(event.assistantText());
                    }
                    if (event.isResult()) {
                        terminal.set(event);
                    }
                    return describeLine(event, delta, now - startMillis);
                })
                .onErrorResume(e -> Flux.just("[error] " + e));

        return Flux.concat(eventLines, Flux.defer(() -> {
            ClaudeEvent result = terminal.get();
            boolean complete = result != null;
            String subtype = complete ? result.resultSubtype() : "";
            String costUsd = complete ? result.costUsd() : "";
            long durationMs = complete ? result.durationMs() : 0L;
            String body = assistantText.toString();

            List<String> tail = new ArrayList<>();
            tail.add(footer(complete, subtype, costUsd, body.length(), durationMs,
                    System.currentTimeMillis() - startMillis));
            for (String line : body.split("\n", -1)) {
                tail.add(line);
            }
            tail.add("</ClaudeSmoke>");
            tail.add("[DONE]");
            return Flux.fromIterable(tail);
        }));
    }

    private static String describeLine(ClaudeEvent event, long deltaMs, long elapsedMs) {
        String prefix = "[+" + deltaMs + "ms/" + elapsedMs + "ms] ";
        if (!event.isAssistant()) {
            return prefix + event.describe();
        }
        return prefix + event.describe() + " preview=" + preview(event.assistantText());
    }

    private static String footer(boolean complete, String subtype, String costUsd,
                                 int assistantChars, long durationMs, long wallMs) {
        return "<ClaudeSmoke complete=\"" + complete
                + "\" subtype=\"" + subtype
                + "\" assistantChars=\"" + assistantChars
                + "\" costUsd=\"" + costUsd
                + "\" durationMs=\"" + durationMs
                + "\" wallMs=\"" + wallMs + "\">";
    }

    private static String preview(String text) {
        String flat = text.replace("\r", "").replace("\n", "\\n");
        return flat.length() <= PREVIEW_CHARS ? flat : flat.substring(0, PREVIEW_CHARS) + "…";
    }
}
