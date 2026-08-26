package io.github.mangomaner.mangobot.module.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.module.agent.debug.AgentDebugRecorder;
import io.github.mangomaner.mangobot.module.agent.middleware.AgentContextCapture;
import io.github.mangomaner.mangobot.module.agent.model.dto.DebugRecordRequest;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.ChatSessionVO;
import io.github.mangomaner.mangobot.module.agent.model.vo.DebugRecordStatusVO;
import io.github.mangomaner.mangobot.module.agent.service.ChatSessionService;
import io.github.mangomaner.mangobot.system.common.BaseResponse;
import io.github.mangomaner.mangobot.system.common.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 调试接口：查看最近一次模型调用发送给模型的完整上下文（分析 token 消耗）
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/debug")
@Tag(name = "Agent 调试")
@RequiredArgsConstructor
public class AgentDebugController {

    private final AgentContextCapture agentContextCapture;
    private final AgentDebugRecorder agentDebugRecorder;
    private final ChatSessionService chatSessionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/context")
    @Operation(summary = "查看最近一次模型调用的完整上下文", description = "返回发送给模型的完整消息列表、工具清单与 Token 用量")
    public BaseResponse<Map<String, Object>> getContext(@RequestParam Integer sessionId) {
        AgentContextCapture.CallContext context = agentContextCapture.get(sessionId);
        if (context == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR.getCode(), null,
                    "暂无该会话的上下文记录（先发起一次对话）");
        }
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("input", objectMapper.readValue(context.inputJson, Map.class));
            result.put("output", context.output);
            result.put("usage", context.usageJson != null
                    ? objectMapper.readValue(context.usageJson, Map.class)
                    : null);
            return new BaseResponse<>(0, result, "");
        } catch (Exception e) {
            log.error("Failed to read captured context for session: {}", sessionId, e);
            return new BaseResponse<>(ErrorCode.SYSTEM_ERROR.getCode(), null, "读取上下文失败");
        }
    }

    @PutMapping("/record")
    @Operation(summary = "开启/关闭会话调试记录", description = "开启后该会话每次模型调用的完整上下文都会追加写入 data/debug/，退出调试模式后仍持续记录")
    public BaseResponse<Void> setRecord(@RequestBody DebugRecordRequest request) {
        if (request == null || request.getSessionId() == null || request.getEnabled() == null) {
            return new BaseResponse<>(ErrorCode.PARAMS_ERROR);
        }
        agentDebugRecorder.setRecording(request.getSessionId(), request.getEnabled());
        return new BaseResponse<>(0, null, "");
    }

    @GetMapping("/record/status")
    @Operation(summary = "查询会话调试记录状态", description = "返回记录开关是否开启，以及已记录的模型调用次数")
    public BaseResponse<DebugRecordStatusVO> getRecordStatus(@RequestParam Integer sessionId) {
        DebugRecordStatusVO vo = new DebugRecordStatusVO();
        vo.setEnabled(agentDebugRecorder.isRecording(sessionId));
        vo.setRecordCount(agentDebugRecorder.recordCount(sessionId));
        return new BaseResponse<>(0, vo, "");
    }

    @GetMapping("/record/summary")
    @Operation(summary = "列出会话已记录调用的轻量摘要", description = "仅返回每条记录的 index/time/usage/triggerText，供列表渲染，避免传输完整上下文")
    public BaseResponse<List<Map<String, Object>>> listRecordSummaries(@RequestParam Integer sessionId) {
        return new BaseResponse<>(0, agentDebugRecorder.listRecordSummaries(sessionId), "");
    }

    @GetMapping("/record/detail")
    @Operation(summary = "取某条调试记录的完整内容", description = "按持久化记录下标（summary 的 index）返回该条记录的完整 time/input/output/usage")
    public BaseResponse<Map<String, Object>> recordDetail(@RequestParam Integer sessionId,
                                                          @RequestParam Integer index) {
        if (sessionId == null || index == null || index < 0) {
            return new BaseResponse<>(ErrorCode.PARAMS_ERROR);
        }
        Map<String, Object> record = agentDebugRecorder.getRecord(sessionId, index);
        if (record == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR.getCode(), null, "未找到对应记录");
        }
        return new BaseResponse<>(0, record, "");
    }

    @DeleteMapping("/record/single")
    @Operation(summary = "删除某条调试记录", description = "按持久化记录下标（record/list 的 0 基下标）删除单条，无需确认")
    public BaseResponse<Void> deleteRecord(@RequestParam Integer sessionId, @RequestParam Integer index) {
        if (sessionId == null || index == null || index < 0) {
            return new BaseResponse<>(ErrorCode.PARAMS_ERROR);
        }
        agentDebugRecorder.removeRecord(sessionId, index);
        return new BaseResponse<>(0, null, "");
    }

    @DeleteMapping("/record/all")
    @Operation(summary = "清空某会话所有调试记录", description = "删除该会话全部已记录内容，记录开关保持不变")
    public BaseResponse<Void> clearRecords(@RequestParam Integer sessionId) {
        if (sessionId == null) {
            return new BaseResponse<>(ErrorCode.PARAMS_ERROR);
        }
        agentDebugRecorder.clearRecords(sessionId);
        return new BaseResponse<>(0, null, "");
    }

    @GetMapping("/session")
    @Operation(summary = "根据来源与 botId/chatId 解析会话", description = "用于调试模式下选中群聊/私聊会话后拿到 sessionId")
    public BaseResponse<ChatSessionVO> resolveSession(@RequestParam String source,
                                                      @RequestParam(required = false) String botId,
                                                      @RequestParam String chatId) {
        SessionSource sessionSource;
        try {
            sessionSource = SessionSource.fromKey(source);
        } catch (IllegalArgumentException e) {
            return new BaseResponse<>(ErrorCode.PARAMS_ERROR);
        }
        ChatSessionVO vo = chatSessionService.getSessionByBotIdAndChatIdOrNull(botId, chatId, sessionSource);
        if (vo == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR.getCode(), null, "未找到对应会话");
        }
        return new BaseResponse<>(0, vo, "");
    }
}
