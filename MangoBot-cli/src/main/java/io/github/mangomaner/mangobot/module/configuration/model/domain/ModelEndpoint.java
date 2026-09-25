package io.github.mangomaner.mangobot.module.configuration.model.domain;

/**
 * 某个模型角色解析出来的完整配置元信息：凭据（地址 / 密钥 / 模型名 / 供应商名）来自
 * {@code model_providers} 与 {@code model_configs}，生成参数（temperature / maxTokens / topP / timeout）
 * 来自 {@code model_configs}。
 *
 * <p>存在的理由：有些运行时拿不到建好的模型对象，只能自己拉起进程并注入环境变量
 * （Claude Code 子进程要的是 {@code ANTHROPIC_BASE_URL} / {@code ANTHROPIC_AUTH_TOKEN} / {@code ANTHROPIC_MODEL}），
 * 或自己发 HTTP 请求。它们需要的是原始配置，不是模型对象。
 *
 * <p>成功与失败装在一个类型里：调用方一次查询就能拿到"能用"或"为什么不能用"，不必为了报错原因再查一遍。
 *
 * @param problem 解析失败的原因；成功时为 {@code null}
 */
public record ModelEndpoint(String baseUrl, String apiKey, String modelName, String providerName,
                            Double temperature, Integer maxTokens, Double topP, Integer timeout,
                            String problem) {

    public static ModelEndpoint of(String baseUrl, String apiKey, String modelName, String providerName,
                                   Double temperature, Integer maxTokens, Double topP, Integer timeout) {
        return new ModelEndpoint(baseUrl, apiKey, modelName, providerName,
                temperature, maxTokens, topP, timeout, null);
    }

    public static ModelEndpoint failed(String problem) {
        return new ModelEndpoint(null, null, null, null, null, null, null, null, problem);
    }

    /** 是否解析成功。成功时三个凭据字段与 {@code providerName} 均非空。 */
    public boolean ok() {
        return problem == null;
    }
}
