package io.github.mangomaner.mangobot.module.agent.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mangomaner.mangobot.module.agent.capability.prompt.SystemPromptComposer;
import io.github.mangomaner.mangobot.module.agent.context.ContextAnalysisService;
import io.github.mangomaner.mangobot.module.agent.context.ContextTranscriptService;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.system.common.BaseResponse;
import io.github.mangomaner.mangobot.system.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 查看某个会话喂给模型的完整上下文。
 *
 * <p>数据源是 claude 自己的转录，不额外记录——转录本来就是完整的，再存一份必然和它对不上。
 */
@RestController
@RequestMapping("/api/agent/context")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "会话上下文", description = "查看会话喂给模型的完整上下文")
public class AgentContextController {

    private final ContextTranscriptService transcriptService;
    private final ContextAnalysisService analysisService;
    private final SystemPromptComposer systemPromptComposer;

    /**
     * 一份转录的元信息 + 全部记录（超长字符串已换成省略标记，图片走 /image 单独取）。
     *
     * <p>{@code systemPrompt} 不在转录里 —— 它是用 {@code --append-system-prompt} 当命令行参数
     * 传进去的，所以这里现拼一份给页面看。
     */
    public record TranscriptVO(String sessionKey, String sessionId, long fileSize, int lineCount,
                               String systemPrompt, List<String> sessionIds, List<RecordVO> records,
                               List<ContextAnalysisService.CallUsage> usage,
                               List<ContextAnalysisService.Compaction> compactions) {
    }

    public record RecordVO(int index, String type, JsonNode json) {
    }

    @GetMapping("/report")
    @Operation(summary = "让 CLI 给出上下文分类明细（会复制一份会话目录再跑，跑完删除）")
    public BaseResponse<String> report(@RequestParam String botId,
                                       @RequestParam String source,
                                       @RequestParam String targetId,
                                       @RequestParam(required = false) String sessionId) {
        SessionSource sessionSource = parseSource(source);
        if (sessionSource == null) {
            return ResultUtils.error(400, "来源不认识：" + source);
        }
        return ResultUtils.success(analysisService.reportFromCli(
                ContextTranscriptService.sessionKey(botId, source, targetId), sessionSource, sessionId));
    }

    @GetMapping("/transcript")
    @Operation(summary = "取一个会话的转录")
    public BaseResponse<TranscriptVO> transcript(@RequestParam String botId,
                                                 @RequestParam String source,
                                                 @RequestParam String targetId,
                                                 @RequestParam(required = false) String sessionId) {
        SessionSource sessionSource = parseSource(source);
        if (sessionSource == null) {
            return ResultUtils.error(400, "来源不认识：" + source);
        }
        String key = ContextTranscriptService.sessionKey(botId, source, targetId);
        String systemPrompt = systemPromptComposer.compose(sessionSource);

        List<String> sessionIds = transcriptService.listSessions(key).stream()
                .map(ContextTranscriptService.SessionFile::sessionId)
                .toList();

        ContextTranscriptService.Transcript transcript = transcriptService.load(key, sessionId);
        if (transcript == null) {
            // 没有转录不是错误：刚建的会话还没跑过任何一轮
            return ResultUtils.success(
                    new TranscriptVO(key, null, 0, 0, systemPrompt, sessionIds,
                            List.of(), List.of(), List.of()));
        }

        List<RecordVO> records = new ArrayList<>();
        for (ContextTranscriptService.Record record : transcript.records()) {
            records.add(new RecordVO(record.index(), record.type(), transcriptService.truncate(record.json())));
        }
        return ResultUtils.success(new TranscriptVO(key, transcript.sessionId(),
                transcript.fileSize(), transcript.lineCount(), systemPrompt, sessionIds, records,
                analysisService.usageByCall(key, transcript.sessionId()),
                analysisService.compactionsOf(key, transcript.sessionId())));
    }

    @GetMapping("/image")
    @Operation(summary = "取转录里某一张图片的原图")
    public ResponseEntity<byte[]> image(@RequestParam String botId,
                                        @RequestParam String source,
                                        @RequestParam String targetId,
                                        @RequestParam String sessionId,
                                        @RequestParam int record,
                                        @RequestParam int block,
                                        @RequestParam(defaultValue = "-1") int inner) {
        if (parseSource(source) == null) {
            return ResponseEntity.badRequest().build();
        }
        String key = ContextTranscriptService.sessionKey(botId, source, targetId);
        byte[] bytes = transcriptService.imageBytes(key, sessionId, record, block, inner);
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        transcriptService.imageMediaType(key, sessionId, record, block, inner)))
                .body(bytes);
    }

    /** 来源不认识时返回 null。 */
    private static SessionSource parseSource(String source) {
        try {
            return SessionSource.fromKey(source);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
