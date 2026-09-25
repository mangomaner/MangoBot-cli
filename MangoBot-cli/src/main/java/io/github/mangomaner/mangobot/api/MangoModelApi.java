package io.github.mangomaner.mangobot.api;

import io.github.mangomaner.mangobot.api.enums.ModelRole;
import io.github.mangomaner.mangobot.module.configuration.core.ModelProvider;
import io.github.mangomaner.mangobot.module.configuration.model.domain.ModelEndpoint;
import io.github.mangomaner.mangobot.module.configuration.model.vo.ModelConfigVO;
import io.github.mangomaner.mangobot.module.configuration.model.vo.ModelRoleVO;

import java.util.List;

/**
 * 模型 API (静态工具类)
 * 提供对 AI 模型配置的访问能力，支持按角色解析模型端点与生成参数。
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
     * 解析指定角色的完整配置元信息（凭据 + 生成参数）。
     *
     * <p>给拿不到建好的模型对象、只能自己拉起进程并注入环境变量或自己发 HTTP 请求的运行时用
     * （Claude Code 子进程要的是 {@code ANTHROPIC_BASE_URL} 这类环境变量）。
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
