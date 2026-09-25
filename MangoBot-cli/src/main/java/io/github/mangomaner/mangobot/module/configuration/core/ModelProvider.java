package io.github.mangomaner.mangobot.module.configuration.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.system.common.ErrorCode;
import io.github.mangomaner.mangobot.events.configuration.ModelRoleChangedEvent;
import io.github.mangomaner.mangobot.system.exception.BusinessException;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 模型提供者实现
 * 统一管理模型实例，支持角色与模型的动态映射
 * 实现 ModelProvider 接口，对外提供模型实例
 *
 * 1. 当模型角色与模型配置的映射关系发生变更时，会发布 ModelRoleChangedEvent 事件
 * 2. 当某个角色对应的模型发生变化时，会发布 ModelRoleChangedEvent 事件
 * 3. 当某个角色在使用某模型时，该模型不能被删除
 */
@Service
@Slf4j
@MangoBotEventListener
public class ModelProvider {

    // 注意，OpenAIChatModel一个实例可以同时有多份连接，因此对于每种角色，只需创建一个实例即可
    private final Map<String, OpenAIChatModel> modelCache = new ConcurrentHashMap<>();

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
     * 初始化模型提供者
     * 加载所有已配置的模型角色
     */
    public void init() {
        log.info("初始化模型提供者...");
        List<ModelRole> roles = modelRoleMapper.selectList(null);
        for (ModelRole role : roles) {
            if (role.getModelConfigId() != null) {
                try {
                    loadModel(role.getRoleKey(), role.getModelConfigId());
                } catch (Exception e) {
                    log.error("加载角色 {} 的模型失败", role.getRoleKey(), e);
                }
            }
        }
        log.info("模型提供者初始化完成，已加载 {} 个模型", modelCache.size());
    }

    @MangoBotEventListener
    public boolean onModelRoleChanged(ModelRoleChangedEvent event) {
        String roleKey = event.getRoleKey();
        log.info("收到模型角色变更事件: {}", roleKey);

        if (event.getNewModelConfigId() != null) {
            loadModel(roleKey, event.getNewModelConfigId());
        } else {
            modelCache.remove(roleKey);
            log.info("角色 {} 的模型已移除", roleKey);
        }
        return true;
    }


    /**
     * 获取指定角色的模型实例
     * @param roleKey 角色标识（main, assistant, image, embedding）
     * @return 模型实例，如果未配置则返回 null
     */
    public OpenAIChatModel getModel(String roleKey) {
        OpenAIChatModel model = modelCache.get(roleKey);
        if (model == null) {
            Long configId = getModelConfigIdByKey(roleKey);
            if (configId != null) {
                loadModel(roleKey, configId);
                model = modelCache.get(roleKey);
            }
        }
        return model;
    }

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
     * 解析指定角色的端点凭据（地址 / 密钥 / 模型名 / 供应商名）。
     *
     * <p>给拿不到 {@link OpenAIChatModel} 实例、只能自己拉起进程并注入环境变量的运行时用。
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
                config.getModelName().trim(), provider.getName());
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

    private void loadModel(String roleKey, Long configId) {
        ModelConfig config = modelConfigMapper.selectById(configId);

        // 若是空模型则清除缓存并返回
        if (config == null || config.getProviderId() == 0) {
            modelCache.remove(roleKey);
            return;
        }

        if (config == null || config.getIsEnabled() != 1) {
            modelCache.remove(roleKey);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模型配置不存在");
        }
        io.github.mangomaner.mangobot.module.configuration.model.domain.ModelProvider provider = modelProviderMapper.selectById(config.getProviderId());
        if (provider == null || provider.getIsEnabled() != 1) {
            modelCache.remove(roleKey);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模型供应商不存在");
        }

        OpenAIChatModel model = OpenAIChatModel.builder()
                .baseUrl(provider.getBaseUrl())
                .apiKey(provider.getApiKey())
                .stream(true)
                .modelName(config.getModelName())
                .build();

        modelCache.put(roleKey, model);
        log.info("加载模型成功: {} -> {} ({})", roleKey, config.getModelName(), provider.getName());
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
