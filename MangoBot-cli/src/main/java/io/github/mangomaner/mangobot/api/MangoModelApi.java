package io.github.mangomaner.mangobot.api;

import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.github.mangomaner.mangobot.api.enums.ModelRole;
import io.github.mangomaner.mangobot.module.configuration.core.ModelProvider;
import io.github.mangomaner.mangobot.module.configuration.model.domain.ModelEndpoint;
import io.github.mangomaner.mangobot.module.configuration.model.vo.ModelConfigVO;
import io.github.mangomaner.mangobot.module.configuration.model.vo.ModelRoleVO;

import java.util.List;

/**
 * 模型 API (静态工具类)
 * 提供对 AI 模型的访问能力，支持按角色获取模型实例。
 */
public class MangoModelApi {

    private static ModelProvider provider;

    private MangoModelApi() {}

    static void setProvider(ModelProvider provider) {
        MangoModelApi.provider = provider;
    }

    private static void checkProvider() {
        if (provider == null) {
            throw new IllegalStateException("MangoModelApi has not been initialized yet.");
        }
    }

    /**
     * 获取指定角色的模型实例
     * @param role 模型角色枚举
     * @return 模型实例，如果未配置则返回 null
     */
    public static OpenAIChatModel getModel(ModelRole role) {
        checkProvider();
        return provider.getModel(role.getRoleKey());
    }

    /**
     * 获取指定角色的模型实例
     * @param roleKey 角色标识（main, assistant, image, embedding）
     * @return 模型实例，如果未配置则返回 null
     */
    public static OpenAIChatModel getModel(String roleKey) {
        checkProvider();
        return provider.getModel(roleKey);
    }

    /**
     * 获取指定角色的模型配置详情
     * @param role 模型角色枚举
     * @return 模型配置详情
     */
    public static ModelConfigVO getModelConfig(ModelRole role) {
        checkProvider();
        return provider.getModelConfig(role.getRoleKey());
    }

    /**
     * 获取指定角色的模型配置详情
     * @param roleKey 角色标识
     * @return 模型配置详情
     */
    public static ModelConfigVO getModelConfig(String roleKey) {
        checkProvider();
        return provider.getModelConfig(roleKey);
    }

    /**
     * 解析指定角色的端点凭据（地址 / 密钥 / 模型名 / 供应商名）。
     *
     * <p>给拿不到 {@link OpenAIChatModel} 实例、只能自己拉起进程并注入环境变量的运行时用
     * （Claude Code 子进程要的是 {@code ANTHROPIC_BASE_URL} 这类环境变量，不是模型对象；
     * 而 {@link OpenAIChatModel} 只暴露 {@code getModelName()}，读不出地址与密钥）。
     *
     * @param role 模型角色枚举
     * @return 永不为 {@code null}；解析失败时 {@link ModelEndpoint#ok()} 为 false
     */
    public static ModelEndpoint resolveEndpoint(ModelRole role) {
        checkProvider();
        return provider.resolveEndpoint(role.getRoleKey());
    }

    /**
     * 获取所有角色配置
     * @return 角色配置列表
     */
    public static List<ModelRoleVO> getAllRoles() {
        checkProvider();
        return provider.getAllRoles();
    }

    /**
     * 获取指定角色的配置
     * @param role 模型角色枚举
     * @return 角色配置
     */
    public static ModelRoleVO getRole(ModelRole role) {
        checkProvider();
        return provider.getRole(role.getRoleKey());
    }

    /**
     * 获取指定角色的配置
     * @param roleKey 角色标识
     * @return 角色配置
     */
    public static ModelRoleVO getRole(String roleKey) {
        checkProvider();
        return provider.getRole(roleKey);
    }

}
