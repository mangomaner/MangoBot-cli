package io.github.mangomaner.mangobot.module.configuration.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.mangomaner.mangobot.events.configuration.ModelRoleChangedEvent;
import io.github.mangomaner.mangobot.system.mapper.configuration.ModelConfigMapper;
import io.github.mangomaner.mangobot.system.mapper.configuration.ModelRoleMapper;
import io.github.mangomaner.mangobot.module.configuration.model.domain.ModelConfig;
import io.github.mangomaner.mangobot.module.configuration.model.domain.ModelEndpoint;
import io.github.mangomaner.mangobot.module.configuration.model.domain.ModelRole;
import io.github.mangomaner.mangobot.module.configuration.model.vo.ModelConfigVO;
import io.github.mangomaner.mangobot.module.configuration.model.vo.ModelRoleVO;
import io.github.mangomaner.mangobot.infra.MangoEventPublisher;
import io.github.mangomaner.mangobot.system.mapper.configuration.ModelProviderMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型提供者实现
 * 统一管理角色到模型配置的映射，对外提供模型的完整配置元信息。
 *
 * <p>当角色与模型配置的映射关系发生变更时，会发布 {@link ModelRoleChangedEvent} 事件，
 * 由需要重建模型连接的运行时（Claude 会话）监听。
 */
@Service
@Slf4j
public class ModelProvider {

    @Resource
    private ModelRoleMapper modelRoleMapper;

    @Resource
    private ModelConfigMapper modelConfigMapper;

    @Resource
    private ModelProviderMapper modelProviderMapper;

    @Resource
    @Lazy
    private MangoEventPublisher mangoEventPublisher;

    /**
     * 获取指定角色的模型配置详情
     * @param roleKey 角色标识
     * @return 模型配置详情
     */
    public ModelConfigVO getModelConfig(String roleKey) {
        Long configId = getModelConfigIdByKey(roleKey);
        if (configId == null) {
            return null;
        }

        ModelConfig config = modelConfigMapper.selectById(configId);
        if (config == null) {
            return null;
        }

        ModelConfigVO vo = new ModelConfigVO();
        vo.setId(config.getId());
        vo.setModelName(config.getModelName());
        vo.setProviderId(config.getProviderId());
        vo.setTemperature(config.getTemperature());
        vo.setMaxTokens(config.getMaxTokens());
        vo.setTopP(config.getTopP());
        vo.setTimeout(config.getTimeout());
        vo.setDescription(config.getDescription());
        vo.setIsEnabled(config.getIsEnabled() == 1);
        vo.setCreatedAt(config.getCreatedAt());
        vo.setUpdatedAt(config.getUpdatedAt());

        io.github.mangomaner.mangobot.module.configuration.model.domain.ModelProvider provider = modelProviderMapper.selectById(config.getProviderId());
        if (provider != null) {
            vo.setProviderName(provider.getName());
        }

        return vo;
    }

    /**
     * 解析指定角色的完整配置元信息（凭据 + 生成参数）。
     *
     * <p>给拿不到建好的模型对象、只能自己拉起进程并注入环境变量或自己发 HTTP 请求的运行时用。
     * model_roles → model_configs → model_providers 这三段关联的读取集中在这里一处，
     * 避免各处自己拼。
     *
     * <p>刻意返回失败原因而不是抛异常：调用点（启动日志、诊断端点）本来就是要把
     * "为什么没配好"讲给人听，抛异常反而让每条调用路径都得写一遍 try/catch。
     *
     * @param roleKey 角色标识（main, assistant, image, embedding）
     * @return 永不为 {@code null}；解析失败时 {@link ModelEndpoint#ok()} 为 false
     */
    public ModelEndpoint resolveEndpoint(String roleKey) {
        LambdaQueryWrapper<ModelRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelRole::getRoleKey, roleKey);
        ModelRole role = modelRoleMapper.selectOne(wrapper);
        if (role == null) {
            return ModelEndpoint.failed("「模型设置」里没有角色 " + roleKey + "（期望 role_key='" + roleKey + "'）");
        }

        Long configId = role.getModelConfigId();
        if (configId == null) {
            return ModelEndpoint.failed("角色「" + role.getRoleName() + "」还没有指定模型，请到「模型设置」里选一个");
        }

        ModelConfig config = modelConfigMapper.selectById(configId);
        if (config == null) {
            return ModelEndpoint.failed("角色「" + role.getRoleName() + "」指向的模型配置不存在（id=" + configId + "）");
        }
        if (config.getProviderId() == null || config.getProviderId() == 0) {
            return ModelEndpoint.failed("模型「" + config.getModelName() + "」没有绑定供应商");
        }
        if (config.getIsEnabled() == null || config.getIsEnabled() != 1) {
            return ModelEndpoint.failed("模型「" + config.getModelName() + "」已被禁用");
        }

        io.github.mangomaner.mangobot.module.configuration.model.domain.ModelProvider provider =
                modelProviderMapper.selectById(config.getProviderId());
        if (provider == null) {
            return ModelEndpoint.failed("模型「" + config.getModelName() + "」的供应商不存在（id="
                    + config.getProviderId() + "）");
        }
        if (provider.getIsEnabled() == null || provider.getIsEnabled() != 1) {
            return ModelEndpoint.failed("供应商「" + provider.getName() + "」已被禁用");
        }
        if (isBlank(provider.getBaseUrl()) || isBlank(provider.getApiKey()) || isBlank(config.getModelName())) {
            return ModelEndpoint.failed("供应商「" + provider.getName() + "」或模型「" + config.getModelName()
                    + "」的地址 / 密钥 / 模型名不完整");
        }

        return ModelEndpoint.of(provider.getBaseUrl().trim(), provider.getApiKey().trim(),
                config.getModelName().trim(), provider.getName(),
                config.getTemperature(), config.getMaxTokens(), config.getTopP(), config.getTimeout());
    }

    /**
     * 获取所有角色配置
     * @return 角色配置列表
     */
    public List<ModelRoleVO> getAllRoles() {
        List<ModelRole> roles = modelRoleMapper.selectList(null);
        return roles.stream()
                .map(this::convertToRoleVO)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定角色的配置
     * @param roleKey 角色标识
     * @return 角色配置
     */
    public ModelRoleVO getRole(String roleKey) {
        LambdaQueryWrapper<ModelRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelRole::getRoleKey, roleKey);
        ModelRole role = modelRoleMapper.selectOne(wrapper);
        return role != null ? convertToRoleVO(role) : null;
    }

    /**
     * 更新角色对应的模型
     * @param roleKey 角色标识
     * @param modelConfigId 模型配置ID
     */
    public void updateRoleModel(String roleKey, Long modelConfigId) {
        LambdaQueryWrapper<ModelRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelRole::getRoleKey, roleKey);
        ModelRole role = modelRoleMapper.selectOne(wrapper);

        if (role == null) {
            log.warn("角色 {} 不存在", roleKey);
            return;
        }

        Long oldConfigId = role.getModelConfigId();
        role.setModelConfigId(modelConfigId);
        role.setUpdatedAt(System.currentTimeMillis());
        modelRoleMapper.updateById(role);

        mangoEventPublisher.publish(new ModelRoleChangedEvent(
                role.getId(),
                roleKey,
                oldConfigId,
                modelConfigId
        ));

        log.info("更新角色 {} 的模型配置: {} -> {}", roleKey, oldConfigId, modelConfigId);
    }

    private Long getModelConfigIdByKey(String roleKey) {
        LambdaQueryWrapper<ModelRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelRole::getRoleKey, roleKey);
        ModelRole role = modelRoleMapper.selectOne(wrapper);
        return role != null ? role.getModelConfigId() : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ModelRoleVO convertToRoleVO(ModelRole role) {
        ModelRoleVO vo = new ModelRoleVO();
        vo.setId(role.getId());
        vo.setRoleKey(role.getRoleKey());
        vo.setRoleName(role.getRoleName());
        vo.setModelConfigId(role.getModelConfigId());
        vo.setDescription(role.getDescription());
        vo.setCreatedAt(role.getCreatedAt());
        vo.setUpdatedAt(role.getUpdatedAt());

        if (role.getModelConfigId() != null) {
            ModelConfig config = modelConfigMapper.selectById(role.getModelConfigId());
            if (config != null) {
                vo.setModelName(config.getModelName());
                io.github.mangomaner.mangobot.module.configuration.model.domain.ModelProvider provider = modelProviderMapper.selectById(config.getProviderId());
                if (provider != null) {
                    vo.setProviderName(provider.getName());
                }
            }
        }

        return vo;
    }
}
