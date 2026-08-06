package io.github.mangomaner.mangobot.adapter.onebot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.mangomaner.mangobot.adapter.model.enums.PlatformType;
import io.github.mangomaner.mangobot.adapter.onebot.model.domain.OneBotConfig;
import io.github.mangomaner.mangobot.adapter.onebot.model.dto.CreateOneBotConfigRequest;
import io.github.mangomaner.mangobot.adapter.onebot.model.dto.UpdateOneBotConfigRequest;
import io.github.mangomaner.mangobot.adapter.model.enums.ConnectionStatus;
import io.github.mangomaner.mangobot.adapter.onebot.model.vo.OneBotConfigVO;
import io.github.mangomaner.mangobot.adapter.onebot.service.OneBotConfigService;
import io.github.mangomaner.mangobot.adapter.onebot.util.ConnectionUrlUtil;
import io.github.mangomaner.mangobot.system.common.ErrorCode;
import io.github.mangomaner.mangobot.infra.websocket.WebSocketServerManager;
import io.github.mangomaner.mangobot.system.exception.BusinessException;
import io.github.mangomaner.mangobot.system.exception.ThrowUtils;
import io.github.mangomaner.mangobot.system.mapper.connection.OneBotConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OneBotConfigServiceImpl extends ServiceImpl<OneBotConfigMapper, OneBotConfig> implements OneBotConfigService {

    private final WebSocketServerManager webSocketServerManager;

    public OneBotConfigServiceImpl(WebSocketServerManager webSocketServerManager) {
        this.webSocketServerManager = webSocketServerManager;
    }

    @Override
    public List<OneBotConfigVO> listAll() {
        return list().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public OneBotConfigVO getById(Long id) {
        OneBotConfig config = super.getById(id);
        ThrowUtils.throwIf(config == null, ErrorCode.NOT_FOUND_ERROR, "配置不存在");
        return convertToVO(config);
    }

    @Override
    @Transactional
    public Long createConfig(CreateOneBotConfigRequest request) {
        OneBotConfig config = new OneBotConfig();
        BeanUtils.copyProperties(request, config);
        config.setUrl(normalizeUrl(request.getUrl()));
        config.setProtocolType(PlatformType.ONEBOT_QQ.getCode());
        config.setEnabled(0);
        config.setConnectionStatus(ConnectionStatus.NOT_STARTED.getCode());
        config.setCreatedAt(System.currentTimeMillis());
        config.setUpdatedAt(System.currentTimeMillis());
        
        save(config);
        return config.getId();
    }

    @Override
    @Transactional
    public void updateConfig(UpdateOneBotConfigRequest request) {
        OneBotConfig config = super.getById(request.getId());
        ThrowUtils.throwIf(config == null, ErrorCode.NOT_FOUND_ERROR, "配置不存在");
        
        ThrowUtils.throwIf(webSocketServerManager.isRunning(request.getId()), 
                ErrorCode.OPERATION_ERROR, "服务器运行中，无法修改配置");
        
        if (request.getName() != null) {
            config.setName(request.getName());
        }
        if (request.getUrl() != null) {
            config.setUrl(normalizeUrl(request.getUrl()));
        }
        if (request.getToken() != null) {
            config.setToken(request.getToken());
        }
        if (request.getDescription() != null) {
            config.setDescription(request.getDescription());
        }
        config.setUpdatedAt(System.currentTimeMillis());
        
        updateById(config);
    }

    @Override
    @Transactional
    public void deleteConfig(Long id) {
        OneBotConfig config = super.getById(id);
        ThrowUtils.throwIf(config == null, ErrorCode.NOT_FOUND_ERROR, "配置不存在");
        
        ThrowUtils.throwIf(webSocketServerManager.isRunning(id), 
                ErrorCode.OPERATION_ERROR, "服务器运行中，无法删除配置");
        
        removeById(id);
    }

    @Override
    @Transactional
    public void startServer(Long id) {
        OneBotConfig config = super.getById(id);
        ThrowUtils.throwIf(config == null, ErrorCode.NOT_FOUND_ERROR, "配置不存在");
        
        if (webSocketServerManager.isRunning(id)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "服务器已在运行中");
        }
        
        try {
            webSocketServerManager.startServer(config);
            config.setEnabled(1);
            config.setConnectionStatus(ConnectionStatus.RUNNING.getCode());
            config.setUpdatedAt(System.currentTimeMillis());
            updateById(config);
        } catch (Exception e) {
            config.setConnectionStatus(ConnectionStatus.ERROR.getCode());
            config.setUpdatedAt(System.currentTimeMillis());
            updateById(config);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "启动服务器失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void stopServer(Long id) {
        OneBotConfig config = super.getById(id);
        ThrowUtils.throwIf(config == null, ErrorCode.NOT_FOUND_ERROR, "配置不存在");
        
        if (!webSocketServerManager.isRunning(id)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "服务器未运行");
        }
        
        webSocketServerManager.stopServer(id);
        config.setEnabled(0);
        config.setConnectionStatus(ConnectionStatus.STOPPED.getCode());
        config.setUpdatedAt(System.currentTimeMillis());
        updateById(config);
    }

    @Override
    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        OneBotConfig config = super.getById(id);
        ThrowUtils.throwIf(config == null, ErrorCode.NOT_FOUND_ERROR, "配置不存在");
        
        if (enabled) {
            startServer(id);
        } else {
            stopServer(id);
        }
    }

    @Override
    public OneBotConfigVO getServerStatus(Long id) {
        OneBotConfig config = super.getById(id);
        ThrowUtils.throwIf(config == null, ErrorCode.NOT_FOUND_ERROR, "配置不存在");
        
        ConnectionStatus status = webSocketServerManager.getStatus(id);
        config.setConnectionStatus(status.getCode());
        
        return convertToVO(config);
    }

    private String normalizeUrl(String url) {
        try {
            return ConnectionUrlUtil.normalize(url);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, e.getMessage());
        }
    }

    private OneBotConfigVO convertToVO(OneBotConfig config) {
        OneBotConfigVO vo = new OneBotConfigVO();
        BeanUtils.copyProperties(config, vo);
        
        ConnectionStatus status = webSocketServerManager.getStatus(config.getId());
        vo.setConnectionStatus(status.getCode());
        
        return vo;
    }

    @Override
    public List<OneBotConfig> listEnabled() {
        return lambdaQuery()
                .eq(OneBotConfig::getEnabled, 1)
                .list();
    }

    @Override
    public void startAllEnabledServers() {
        List<OneBotConfig> enabledConfigs = listEnabled();
        if (enabledConfigs.isEmpty()) {
            log.info("没有已启用的 WebSocket 服务器配置");
            return;
        }
        
        log.info("开始启动 {} 个已启用的 WebSocket 服务器", enabledConfigs.size());
        int successCount = 0;
        int failCount = 0;
        
        for (OneBotConfig config : enabledConfigs) {
            try {
                if (!webSocketServerManager.isRunning(config.getId())) {
                    webSocketServerManager.startServer(config);
                    config.setConnectionStatus(ConnectionStatus.RUNNING.getCode());
                    config.setUpdatedAt(System.currentTimeMillis());
                    updateById(config);
                    successCount++;
                    log.info("WebSocket 服务器启动成功: {} ({})", config.getName(), config.getUrl());
                } else {
                    log.info("WebSocket 服务器已在运行中: {}", config.getName());
                    successCount++;
                }
            } catch (Exception e) {
                failCount++;
                log.error("WebSocket 服务器启动失败: {} ({}) - {}", config.getName(), config.getUrl(), e.getMessage());
                try {
                    config.setConnectionStatus(ConnectionStatus.ERROR.getCode());
                    config.setUpdatedAt(System.currentTimeMillis());
                    updateById(config);
                } catch (Exception updateEx) {
                    log.error("更新服务器状态失败", updateEx);
                }
            }
        }
        
        log.info("WebSocket 服务器启动完成: 成功 {}, 失败 {}", successCount, failCount);
    }
}
