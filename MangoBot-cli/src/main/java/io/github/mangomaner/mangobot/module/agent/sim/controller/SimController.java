package io.github.mangomaner.mangobot.module.agent.sim.controller;

import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
import io.github.mangomaner.mangobot.module.agent.sim.model.domain.SimMessage;
import io.github.mangomaner.mangobot.module.agent.sim.model.domain.SimSession;
import io.github.mangomaner.mangobot.module.agent.sim.harness.SimSend;
import io.github.mangomaner.mangobot.module.agent.sim.service.SimSessionService;
import io.github.mangomaner.mangobot.system.common.BaseResponse;
import io.github.mangomaner.mangobot.system.common.ResultUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模拟对话台的接口。由<b>外部 agent</b>驱动，不是一个给人点的表单页。
 *
 * <p>四个动作就是全部：开局 → 一句句喂 → 读回转录 → 评判落库。前端只读（列表 + 转录 + 打分）。
 *
 * <p>路径放在 {@code /api/dev/sim} 下：它是开发/评测期的工具，不是给生产用户用的功能面。
 */
@RestController
@RequestMapping("/api/dev/sim")
@RequiredArgsConstructor
public class SimController {

    private final SimSessionService simService;
    private final ClaudeRuntimeManager runtimeManager;

    /**
     * 开一场模拟。
     *
     * <p>会先把上一场残留的连接收掉——同一时刻只能有一场（虚拟号与会话键都是固定的，并发会串场）。
     */
    @PostMapping("/start")
    public BaseResponse<SimSession> start(@RequestBody StartRequest request) {
        return ResultUtils.success(simService.start(request.title(), request.personaLabel(), request.seedRef()));
    }

    /**
     * 以某个群友的身份发一句，等 bot 把这一轮说完。
     *
     * <p>返回 bot 这一轮真正发出去的消息；空数组表示它选择不说话，这是正常结果，不是失败。
     * 抛错才是失败（消息没进链路 / 这场已经收了）。
     */
    @PostMapping("/{id}/say")
    public BaseResponse<List<SimSend>> say(@PathVariable Integer id, @RequestBody SayRequest request) {
        return ResultUtils.success(
                simService.say(id, request.speaker(), request.qq(), request.text()));
    }

    /** 这场对话到目前为止的完整转录。 */
    @GetMapping("/{id}/transcript")
    public BaseResponse<List<SimMessage>> transcript(@PathVariable Integer id) {
        return ResultUtils.success(simService.messages(id));
    }

    /**
     * 评判这场对话并落库。
     *
     * <p>judge 是独立会话（无工具、无插件、无 MCP），按几个角度给分并引用原文作证据。
     * {@code seedExcerpt} 可选：给 judge 一份"这个群平时怎么说话"的参照。
     */
    @PostMapping("/{id}/review")
    public BaseResponse<SimSession> review(@PathVariable Integer id,
                                           @RequestBody(required = false) ReviewRequest request) {
        String seedExcerpt = request == null ? null : request.seedExcerpt();
        return ResultUtils.success(simService.review(id, seedExcerpt));
    }

    @GetMapping("/sessions")
    public BaseResponse<List<SimSession>> sessions(@RequestParam(defaultValue = "50") int limit) {
        return ResultUtils.success(simService.listRecent(limit));
    }

    @GetMapping("/sessions/{id}")
    public BaseResponse<DetailVO> detail(@PathVariable Integer id) {
        SimSession session = simService.findSession(id)
                .orElseThrow(() -> new IllegalArgumentException("没有这场模拟：" + id));
        return ResultUtils.success(new DetailVO(session, simService.messages(id)));
    }

    /** 运行时是否就绪——没配好模型就没有评判可言，前端靠它给出提示。 */
    @GetMapping("/status")
    public BaseResponse<StatusVO> status() {
        return ResultUtils.success(
                new StatusVO(runtimeManager.isReady(), runtimeManager.notReadyReason()));
    }

    public record StartRequest(String title, String personaLabel, String seedRef) {
    }

    public record SayRequest(String speaker, Long qq, String text) {
    }

    public record ReviewRequest(String seedExcerpt) {
    }

    public record DetailVO(SimSession session, List<SimMessage> messages) {
    }

    public record StatusVO(boolean ready, String reason) {
    }
}
