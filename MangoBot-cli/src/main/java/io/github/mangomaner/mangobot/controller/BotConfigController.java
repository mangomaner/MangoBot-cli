package io.github.mangomaner.mangobot.controller;

import io.github.mangomaner.mangobot.common.BaseResponse;
import io.github.mangomaner.mangobot.common.ResultUtils;
import io.github.mangomaner.mangobot.manager.websocket.BotConnectionManager;
import io.github.mangomaner.mangobot.model.dto.config.UpdateConfigByKeyRequest;
import io.github.mangomaner.mangobot.model.dto.config.UpdateConfigRequest;
import io.github.mangomaner.mangobot.model.onebot.api.response.LoginInfo;
import io.github.mangomaner.mangobot.model.vo.AiTestResultVo;
import io.github.mangomaner.mangobot.model.vo.ConfigVO;
import io.github.mangomaner.mangobot.service.MangobotConfigService;
import io.github.mangomaner.mangobot.service.OneBotApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/config")
@Slf4j
public class BotConfigController {
    @Resource
    private BotConnectionManager botConnectionManager;
    @Resource
    private OneBotApiService oneBotApiService;
    @Resource
    private MangobotConfigService mangobotConfigService;

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

    @GetMapping("/list")
    @Operation(summary = "获取所有配置")
    public BaseResponse<List<ConfigVO>> getAllConfigs() {
        List<ConfigVO> configs = mangobotConfigService.getAllConfigs();
        return ResultUtils.success(configs);
    }

    @GetMapping("/key/{configKey}")
    @Operation(summary = "根据配置键获取配置")
    public BaseResponse<ConfigVO> getConfigByKey(
            @Parameter(description = "配置键") @PathVariable String configKey) {
        ConfigVO config = mangobotConfigService.getConfigByKey(configKey);
        return ResultUtils.success(config);
    }

    @GetMapping("/id/{id}")
    @Operation(summary = "根据配置ID获取配置")
    public BaseResponse<ConfigVO> getConfigById(
            @Parameter(description = "配置ID") @PathVariable Integer id) {
        ConfigVO config = mangobotConfigService.getConfigById(id);
        return ResultUtils.success(config);
    }

    /**
     * 根据配置ID测试对应配置的Ai模型
     * @param id
     * @return
     */
    @PostMapping("/AiModel/test")
    @Operation(summary = "测试Ai模型")
    public BaseResponse<AiTestResultVo> testAiModel(
            @Parameter(description = "配置ID") @RequestParam Integer id) {
        String result = mangobotConfigService.getConfigById(id).getConfigValue();
        AiTestResultVo aiTestResultVo = new AiTestResultVo();
        aiTestResultVo.setSuccess(true);
        aiTestResultVo.setResult(result);
        return ResultUtils.success(aiTestResultVo);
    }


    @PostMapping("/key")
    @Operation(summary = "根据配置键修改配置")
    public BaseResponse<Boolean> updateConfigByKey(
            @Valid @RequestBody UpdateConfigByKeyRequest request) {
        Boolean result = mangobotConfigService.updateConfigByKey(request);
        return ResultUtils.success(result);
    }

    @PostMapping("/id")
    @Operation(summary = "根据配置ID修改配置")
    public BaseResponse<Boolean> updateConfigById(
            @Valid @RequestBody UpdateConfigRequest request) {
        Boolean result = mangobotConfigService.updateConfigById(request);
        return ResultUtils.success(result);
    }
}
