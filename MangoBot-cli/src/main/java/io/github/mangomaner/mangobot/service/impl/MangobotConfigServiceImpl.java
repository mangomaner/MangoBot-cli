package io.github.mangomaner.mangobot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.mangomaner.mangobot.model.domain.MangobotConfig;
import io.github.mangomaner.mangobot.model.dto.UpdateConfigByKeyRequest;
import io.github.mangomaner.mangobot.model.dto.UpdateConfigRequest;
import io.github.mangomaner.mangobot.model.vo.ConfigVO;
import io.github.mangomaner.mangobot.service.MangobotConfigService;
import io.github.mangomaner.mangobot.mapper.MangobotConfigMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
* @author mangoman
* @description 针对表【mangobot_config】的数据库操作Service实现
* @createDate 2026-01-17 13:11:01
*/
@Service
public class MangobotConfigServiceImpl extends ServiceImpl<MangobotConfigMapper, MangobotConfig>
    implements MangobotConfigService{

    @Override
    public List<ConfigVO> getAllConfigs() {
        List<MangobotConfig> configs = this.list();
        return configs.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public ConfigVO getConfigByKey(String configKey) {
        LambdaQueryWrapper<MangobotConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MangobotConfig::getConfigKey, configKey);
        MangobotConfig config = this.getOne(wrapper);
        return convertToVO(config);
    }

    @Override
    public ConfigVO getConfigById(Integer id) {
        MangobotConfig config = this.getById(id);
        return convertToVO(config);
    }

    @Override
    public Boolean updateConfigByKey(UpdateConfigByKeyRequest request) {
        LambdaQueryWrapper<MangobotConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MangobotConfig::getConfigKey, request.getConfigKey());
        MangobotConfig config = this.getOne(wrapper);
        if (config == null) {
            return false;
        }
        config.setConfigValue(request.getConfigValue());
        return this.updateById(config);
    }

    @Override
    public Boolean updateConfigById(UpdateConfigRequest request) {
        MangobotConfig config = this.getById(request.getId());
        if (config == null) {
            return false;
        }
        config.setConfigValue(request.getConfigValue());
        return this.updateById(config);
    }

    private ConfigVO convertToVO(MangobotConfig config) {
        if (config == null) {
            return null;
        }
        ConfigVO vo = new ConfigVO();
        BeanUtils.copyProperties(config, vo);
        return vo;
    }
}




