package io.github.mangomaner.mangobot.module.agent.sim.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.api.enums.ModelRole;
import io.github.mangomaner.mangobot.infra.claude.session.ClaudeEvent;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
import io.github.mangomaner.mangobot.infra.claude.session.ClaudeSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 给一场模拟对话打分。
 *
 * <p><b>打分的是大模型，不是规则。</b>像不像群里的人这件事，靠"句尾有没有句号""沉默比多少"这类
 * 规则量不出来——那些是诊断统计，不是质量。所以这里起一个<b>独立的 judge 会话</b>，把整段转录交给它，
 * 让它按几个角度给分并引用原文作证据。
 *
 * <p>judge 会话刻意是"无工具、无插件、无 MCP"的（见 {@code ClaudeRuntimeManager.newJudgeSession}）：
 * 它只需读一段文本、吐一段 JSON，多一个工具就多一份自作主张的面，也多一份把转录里的内容当指令执行的
 * 风险（剧本里全是祈使句）。
 *
 * <p>提示词按既有的实测结论写：<b>明确允许沉默是正确答案</b>、<b>明令禁止因为"说得多"就给高分</b>
 * （judge 有冗长偏置）、<b>每条分必须带原文证据</b>。没有这三条，judge 会系统性偏爱话多的那一方。
 */
@Component
@Slf4j
public class SimReviewer {

    public static final String PROMPT_VERSION = "sim-review-v1";

    private static final long REVIEW_TIMEOUT_SECONDS = 300L;
    private static final int RAW_KEEP_CHARS = 8000;

    /** 打分角度。改这里等于改口径，记得同时改 {@link #PROMPT_VERSION}。 */
    private static final List<String> DIMENSIONS = List.of(
            "真人感", "接话时机", "接话内容", "语言形式", "群内契合");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClaudeRuntimeManager runtimeManager;

    public SimReviewer(ClaudeRuntimeManager runtimeManager) {
        this.runtimeManager = runtimeManager;
    }

    /**
     * 评判一场模拟对话。
     *
     * @param sessionId 会话 id；judge 会话键由它派生，自成一路、不与聊天会话键相撞
     * @param transcript 转录文本，形如 {@code 昵称: 内容}，一行一条
     * @param seedExcerpt 参考语料摘录（可空）：让 judge 拿真实群的口吻作参照，而不是凭印象
     */
    public SimReview review(Integer sessionId, String transcript, String seedExcerpt) {
        ClaudeSession judge = null;
        try {
            judge = runtimeManager.newJudgeSession(
                    "sim-review:" + sessionId, ModelRole.ASSISTANT, systemPrompt());
            Flux<ClaudeEvent> flux = judge.sendTurn(userPrompt(transcript, seedExcerpt));
            List<ClaudeEvent> events = flux.collectList()
                    .block(Duration.ofSeconds(REVIEW_TIMEOUT_SECONDS));

            if (events == null) {
                return SimReview.failed("评判没有返回结果");
            }
            String subtype = "";
            StringBuilder text = new StringBuilder();
            for (ClaudeEvent event : events) {
                if (event.isResult()) {
                    subtype = String.valueOf(event.resultSubtype());
                } else if (event.isAssistant()) {
                    String t = event.assistantText();
                    if (t != null && !t.isBlank()) {
                        text.append(t);
                    }
                }
            }
            String raw = text.toString();
            if (!"success".equals(subtype)) {
                return SimReview.failed("评判这一轮没跑成功：subtype=" + subtype, raw);
            }
            return parse(raw);
        } catch (Exception e) {
            log.error("模拟对话评判失败：sessionId={}", sessionId, e);
            return SimReview.failed(e.toString());
        } finally {
            if (judge != null) {
                // 一次性会话不在常驻 map 里，没人替它回收
                judge.close();
            }
        }
    }

    // ========================================================================
    // 提示词
    // ========================================================================

    private static String systemPrompt() {
        return """
                你在评审一段 QQ 群聊记录，判断其中标着「bot」的那个人说话像不像群里一个真人。
                其他群友是真人语料的改编，拿它们当参照。

                只评 bot，不要评群友。

                转录的读法：
                - 一行一条，带昵称的是群友，带 `bot:` 的是被测 bot。
                - **某条群友消息后面没有 `bot:` 行，就表示 bot 那一轮没说话。**
                  群里真人经常不接话，所以这一点要当信息读，不要当"漏了"。

                按这 %d 个角度各给 1~5 分，每一项都要引用原文作证据（证据必须是转录里出现过的原句）：
                %s

                打分标准（重要）：
                - 5 分＝看不出这是程序；3 分＝不违和，但细看能觉出来；1 分＝一眼假（客服腔、助理腔、
                  自曝是 AI、动辄长篇大论）。
                - **沉默常常是正确答案。** bot 在某处没说话而那里本来就该安静，这是加分项，不是遗漏。
                - **绝不因为 bot 说得多、说得全就给高分。** 群里真人说话短、常常只补一句、也常常不接。
                  啰嗦、总结、安慰、主动张罗事情，都是扣分项。
                - **反过来也一样：几乎每条都接、句句都接得住，也不是真人。** 群里真人是对着空气各说
                  各的，谁也不负责把所有话都托住。每条都回，本身就是一种"在岗"。
                - 拿不准就给中间分，并在证据里说明拿不准在哪。

                最后写一段「哪里最不像」，点名最具体的那个毛病。

                只输出一个 JSON 代码块，不要有别的内容：
                ```json
                {
                  "verdict": "一句话总评",
                  "scores": [
                    {"dimension": "真人感", "score": 3, "evidence": "转录里的原句"}
                  ],
                  "worst": "哪里最不像"
                }
                ```
                """.formatted(DIMENSIONS.size(),
                String.join("、", DIMENSIONS.stream().map(d -> "「" + d + "」").toList()));
    }

    private static String userPrompt(String transcript, String seedExcerpt) {
        StringBuilder sb = new StringBuilder();
        sb.append("这是这次的群聊记录（按时间顺序）：\n\n").append(transcript);
        if (seedExcerpt != null && !seedExcerpt.isBlank()) {
            sb.append("\n\n---\n下面是从真实群聊里摘的片段，用来对照这个群平时怎么说话：\n\n")
                    .append(seedExcerpt);
        }
        return sb.toString();
    }

    // ========================================================================
    // 解析
    // ========================================================================

    private static SimReview parse(String raw) {
        String json = extractJson(raw);
        if (json == null) {
            return SimReview.failed("评判没有吐出可解析的 JSON", raw);
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            String verdict = node.path("verdict").asText("");
            String worst = node.path("worst").asText("");

            List<SimScore> scores = new ArrayList<>();
            for (JsonNode item : node.path("scores")) {
                scores.add(new SimScore(
                        item.path("dimension").asText(""),
                        item.path("score").isInt() ? item.path("score").asInt() : null,
                        item.path("evidence").asText("")));
            }
            if (scores.isEmpty()) {
                return SimReview.failed("评判给了 JSON 但 scores 是空的", raw);
            }
            String combined = worst.isBlank() ? verdict : verdict + "（最不像的地方：" + worst + "）";
            return new SimReview(combined, scores, truncate(raw), null);
        } catch (Exception e) {
            log.warn("评判 JSON 解析失败", e);
            return SimReview.failed("评判 JSON 解析失败：" + e.getMessage(), raw);
        }
    }

    /** 从回复里抠出那个 json 代码块；没有围栏就退回整段。 */
    private static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int fence = raw.indexOf("```");
        if (fence >= 0) {
            int start = raw.indexOf('\n', fence);
            int end = raw.indexOf("```", fence + 3);
            if (start > 0 && end > start) {
                return raw.substring(start + 1, end).trim();
            }
        }
        int brace = raw.indexOf('{');
        int lastBrace = raw.lastIndexOf('}');
        if (brace >= 0 && lastBrace > brace) {
            return raw.substring(brace, lastBrace + 1);
        }
        return null;
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= RAW_KEEP_CHARS ? text : text.substring(0, RAW_KEEP_CHARS);
    }

    // ========================================================================
    // 结果
    // ========================================================================

    /** 一个角度的分与证据。{@code score} 为 null 表示 judge 没给可识别的整数分。 */
    public record SimScore(String dimension, Integer score, String evidence) {
    }

    /**
     * 评判结果。
     *
     * <p>{@code error} 非空表示"这次评判本身没跑成"，与"跑成了但结论不好"必须区分开——
     * 前者是基建问题，后者是模型行为问题，混在一起会把基建故障读成模型退化。
     * {@code raw} 一律保留，排查要靠它。
     */
    public record SimReview(String verdict, List<SimScore> scores, String raw, String error) {

        static SimReview failed(String error) {
            return new SimReview(null, List.of(), null, error);
        }

        static SimReview failed(String error, String raw) {
            return new SimReview(null, List.of(), truncate(raw), error);
        }
    }
}
