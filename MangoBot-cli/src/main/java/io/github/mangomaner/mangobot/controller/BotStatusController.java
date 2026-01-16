package io.github.mangomaner.mangobot.controller;

import io.github.mangomaner.mangobot.common.BaseResponse;
import io.github.mangomaner.mangobot.common.ResultUtils;
import io.github.mangomaner.mangobot.manager.websocket.BotConnectionManager;
import io.github.mangomaner.mangobot.model.onebot.api.response.LoginInfo;
import io.github.mangomaner.mangobot.service.OneBotApiService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/bot")
@Slf4j
public class BotStatusController {
    @Resource
    private BotConnectionManager botConnectionManager;
    @Resource
    private OneBotApiService oneBotApiService;
    @GetMapping("/connectedBots")
    @Operation(summary = "获取所有已连接的bot")
    public BaseResponse<List<LoginInfo>> getConnectedBots() {
        List<Long> bots = botConnectionManager.getAllBotQQ();
        List<LoginInfo> loginInfos = new ArrayList<>();
        if (!bots.isEmpty()) {
            for (Long bot : bots) {
                LoginInfo result = oneBotApiService.getLoginInfo(bot);
                LoginInfo loginInfo = new LoginInfo();
                loginInfo.setUserId(result.getUserId());
                loginInfo.setNickname(result.getNickname());
                loginInfos.add(loginInfo);
            }
        }
        return ResultUtils.success(loginInfos);
    }
}
