package io.github.mangomaner.mangobot.service;

import io.github.mangomaner.mangobot.model.domain.MangobotConfig;
import io.github.mangomaner.mangobot.model.dto.config.CreateConfigRequest;
import io.github.mangomaner.mangobot.model.dto.config.UpdateConfigByKeyRequest;
import io.github.mangomaner.mangobot.model.dto.config.UpdateConfigRequest;
import io.github.mangomaner.mangobot.model.vo.ConfigVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author mangoman
* @description 针对表【mangobot_config】的数据库操作Service
* @createDate 2026-01-17 13:11:01
*/
public interface MangobotConfigService extends IService<MangobotConfig> {

    void registeConfig(CreateConfigRequest request);

    List<ConfigVO> getAllConfigs();

    ConfigVO getConfigByKey(String configKey);

    ConfigVO getConfigById(Long id);

    Boolean updateConfigByKey(UpdateConfigByKeyRequest request);

    Boolean updateConfigById(UpdateConfigRequest request);
}
