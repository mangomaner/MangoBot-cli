package io.github.mangomaner.mangobot.module.agent.capability.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentJavaToolConfig;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.service.AgentJavaToolConfigService;
import io.github.mangomaner.mangobot.annotation.MangoTool;
import io.github.mangomaner.mangobot.api.MangoToolApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 工具注册服务
 * <p>
 * 负责管理 Java 工具的注册、更新和注销，支持细粒度的来源控制。
 *
 * <h3>注册方式</h3>
 * <ul>
 *   <li>工具类注册 - 通过反射创建实例</li>
 *   <li>工具实例注册 - 直接使用传入的实例</li>
 *   <li>工具工厂注册 - 每次新建实例</li>
 * </ul>
 *
 * <h3>来源控制</h3>
 * <p>通过 {@link SessionSource} 指定工具可在哪些场景使用：
 * <ul>
 *   <li>WEB - Web端对话</li>
 *   <li>GROUP - 群聊场景</li>
 *   <li>PRIVATE - 私聊场景</li>
 * </ul>
 *
 * @see MangoToolApi
 * @see SessionSource
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ToolRegistrationService {

    // ==================== 依赖 ====================

    private final AgentJavaToolConfigService toolConfigService;
    private final JavaToolLoader javaToolLoader;
    private final ObjectMapper objectMapper;

    // ==================== 公共 API ====================

    /**
     * 注册工具类（支持所有来源）
     *
     * @param toolClass 工具类
     * @param args      构造参数（可选）
     * @return 工具配置 ID
     */
    public Integer registerTool(Class<?> toolClass, Object... args) {
        return registerToolWithSource(toolClass, args, null, null);
    }

    /**
     * 注册<b>内置</b>工具：新建那一次直接启用，已存在的行不动 {@code enabled}。
     *
     * <p>区别只在 {@code enabled} 初值。走 {@link #registerTool} 的插件工具默认禁用、等用户去能力页打开，
     * 那是对的——插件注册的东西不该自动生效。但内置工具是产品自身的行为（如 QQ 发送能力），
     * 新装环境首次注册出来若是禁用的，bot 直接哑巴，且用户不知道要去打开哪个开关。
     * 已存在的行保持原样，用户手动关掉就一直是关的。
     *
     * @param toolClass        工具类
     * @param availableSources 支持的来源列表；内核里只有这些来源看得到它
     * @return 工具配置 ID
     */
    public Integer registerBuiltinTool(Class<?> toolClass, List<SessionSource> availableSources) {
        String className = toolClass.getName();
        ToolMetadata metadata = extractMetadata(toolClass);

        tryCreateAndCacheInstance(toolClass, null, className);

        return persistToolConfig(
                className,
                metadata,
                null,
                JavaToolLoader.LOAD_TYPE_NO_ARGS,
                null,
                availableSources,
                "Builtin tool",
                true
        );
    }

    /**
     * 注册工具类（指定插件，支持所有来源）
     *
     * @param toolClass 工具类
     * @param args      构造参数（可选）
     * @param pluginId  插件 ID
     * @return 工具配置 ID
     */
    public Integer registerToolWithPluginId(Class<?> toolClass, Object[] args, Integer pluginId) {
        return registerToolWithSource(toolClass, args, pluginId, null);
    }

    /**
     * 注册工具类（指定插件和来源）
     *
     * @param toolClass        工具类
     * @param args             构造参数（可选）
     * @param pluginId         插件 ID
     * @param availableSources 支持的来源列表（null 表示支持所有来源）
     * @return 工具配置 ID
     */
    public Integer registerToolWithPluginId(
            Class<?> toolClass,
            Object[] args,
            Integer pluginId,
            List<SessionSource> availableSources) {
        return registerToolWithSource(toolClass, args, pluginId, availableSources);
    }

    /**
     * 注册工具实例（支持所有来源）
     *
     * @param toolInstance 工具实例
     * @param pluginId     插件 ID（可选）
     * @return 工具配置 ID
     */
    public Integer registerToolInstance(Object toolInstance, Integer pluginId) {
        return registerToolInstanceWithSource(toolInstance, pluginId, null);
    }

    /**
     * 注册工具实例（指定来源）
     *
     * @param toolInstance     工具实例
     * @param pluginId         插件 ID（可选）
     * @param availableSources 支持的来源列表（null 表示支持所有来源）
     * @return 工具配置 ID
     */
    public Integer registerToolInstanceWithSource(
            Object toolInstance,
            Integer pluginId,
            List<SessionSource> availableSources) {

        String className = toolInstance.getClass().getName();
        ToolMetadata metadata = extractMetadata(toolInstance.getClass());

        javaToolLoader.registerInstance(className, toolInstance);

        return persistToolConfig(
                className,
                metadata,
                null,
                JavaToolLoader.LOAD_TYPE_INSTANCE,
                pluginId,
                availableSources,
                "Tool instance",
                false
        );
    }

    /**
     * 注册工具工厂（支持所有来源）
     *
     * @param toolClass 工具类
     * @param factory   工厂方法
     * @param pluginId  插件 ID（可选）
     * @return 工具配置 ID
     */
    public Integer registerToolFactory(Class<?> toolClass, Supplier<Object> factory, Integer pluginId) {
        return registerToolFactoryWithSource(toolClass, factory, pluginId, null);
    }

    /**
     * 注册工具工厂（指定来源）
     *
     * @param toolClass        工具类
     * @param factory          工厂方法
     * @param pluginId         插件 ID（可选）
     * @param availableSources 支持的来源列表（null 表示支持所有来源）
     * @return 工具配置 ID
     */
    public Integer registerToolFactoryWithSource(
            Class<?> toolClass,
            Supplier<Object> factory,
            Integer pluginId,
            List<SessionSource> availableSources) {

        String className = toolClass.getName();
        ToolMetadata metadata = extractMetadata(toolClass);

        javaToolLoader.registerFactory(className, factory);

        return persistToolConfig(
                className,
                metadata,
                null,
                JavaToolLoader.LOAD_TYPE_FACTORY,
                pluginId,
                availableSources,
                "Tool factory",
                false
        );
    }

    /**
     * 注销工具
     *
     * @param className 类全限定名
     */
    public void unregisterTool(String className) {
        javaToolLoader.registerInstance(className, null);
        javaToolLoader.registerFactory(className, null);
        toolConfigService.deleteByClassName(className);
        log.info("Tool unregistered: {}", className);
    }

    // ==================== 私有方法 ====================

    /**
     * 注册工具类的核心实现
     */
    private Integer registerToolWithSource(
            Class<?> toolClass,
            Object[] args,
            Integer pluginId,
            List<SessionSource> availableSources) {

        String className = toolClass.getName();
        ToolMetadata metadata = extractMetadata(toolClass);

        // 确定加载类型和序列化参数
        boolean hasArgs = args != null && args.length > 0;
        String loadType = hasArgs
                ? JavaToolLoader.LOAD_TYPE_WITH_ARGS
                : JavaToolLoader.LOAD_TYPE_NO_ARGS;
        String argsJson = hasArgs ? serializeArgs(args) : null;

        // 尝试创建并缓存实例
        tryCreateAndCacheInstance(toolClass, args, className);

        // 持久化配置
        return persistToolConfig(
                className,
                metadata,
                argsJson,
                loadType,
                pluginId,
                availableSources,
                "Tool",
                false
        );
    }

    /**
     * 尝试创建并缓存工具实例
     */
    private void tryCreateAndCacheInstance(Class<?> toolClass, Object[] args, String className) {
        try {
            Object instance = createInstance(toolClass, args);
            javaToolLoader.registerInstance(className, instance);
            log.debug("Tool instance created and cached: {}", className);
        } catch (Exception e) {
            log.warn("Failed to create tool instance, will try lazy loading: {}", className, e);
        }
    }

    /**
     * 持久化工具配置（新建或更新）
     */
    private Integer persistToolConfig(
            String className,
            ToolMetadata metadata,
            String constructorArgs,
            String loadType,
            Integer pluginId,
            List<SessionSource> availableSources,
            String logPrefix,
            boolean enabledOnCreate) {

        String availableListJson = buildAvailableListJson(availableSources);
        AgentJavaToolConfig existingConfig = toolConfigService.getByClassName(className);

        if (existingConfig != null) {
            return updateExistingConfig(existingConfig, metadata, constructorArgs, loadType, pluginId, availableListJson, logPrefix);
        } else {
            return createNewConfig(className, metadata, constructorArgs, loadType, pluginId, availableListJson, logPrefix, enabledOnCreate);
        }
    }

    /**
     * 更新现有配置
     */
    private Integer updateExistingConfig(
            AgentJavaToolConfig config,
            ToolMetadata metadata,
            String constructorArgs,
            String loadType,
            Integer pluginId,
            String availableListJson,
            String logPrefix) {

        config.setToolName(metadata.name());
        config.setDescription(metadata.description());
        config.setCategory(metadata.category());
        if (constructorArgs != null) {
            config.setConstructorArgs(constructorArgs);
        }
        config.setLoadType(loadType);
        config.setPluginId(pluginId);
        config.setAvailableList(availableListJson);

        toolConfigService.updateById(config);
        log.info("{} updated: {} (enabled={}, sources={})",
                logPrefix, config.getClassName(), config.getEnabled(), availableListJson);

        return config.getId();
    }

    /**
     * 创建新配置
     */
    private Integer createNewConfig(
            String className,
            ToolMetadata metadata,
            String constructorArgs,
            String loadType,
            Integer pluginId,
            String availableListJson,
            String logPrefix,
            boolean enabledOnCreate) {

        AgentJavaToolConfig config = new AgentJavaToolConfig();
        config.setClassName(className);
        config.setConstructorArgs(constructorArgs);
        config.setToolName(metadata.name());
        config.setDescription(metadata.description());
        config.setCategory(metadata.category());
        config.setLoadType(loadType);
        config.setPluginId(pluginId);
        config.setEnabled(enabledOnCreate);
        config.setAvailableList(availableListJson);
        config.setEnabledList(availableListJson);

        toolConfigService.save(config);
        log.info("{} registered: {} (enabled={}, sources={})",
                logPrefix, className, enabledOnCreate, availableListJson);

        return config.getId();
    }

    /**
     * 构建可用来源列表的 JSON 字符串
     */
    private String buildAvailableListJson(List<SessionSource> availableSources) {
        if (availableSources == null || availableSources.isEmpty()) {
            return getAllSourcesJson();
        }

        List<String> sourceKeys = availableSources.stream()
                .map(SessionSource::getSourceKey)
                .collect(Collectors.toList());

        try {
            return objectMapper.writeValueAsString(sourceKeys);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize available sources, using default", e);
            return getAllSourcesJson();
        }
    }

    /**
     * 获取所有来源的 JSON 字符串
     */
    private String getAllSourcesJson() {
        List<String> allSourceKeys = Arrays.stream(SessionSource.values())
                .map(SessionSource::getSourceKey)
                .collect(Collectors.toList());

        try {
            return objectMapper.writeValueAsString(allSourceKeys);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize all sources, using hardcoded default", e);
            return "[\"web\",\"group\",\"private\"]";
        }
    }

    /**
     * 从类中提取工具元数据
     */
    private ToolMetadata extractMetadata(Class<?> toolClass) {
        MangoTool annotation = toolClass.getAnnotation(MangoTool.class);

        if (annotation != null) {
            return new ToolMetadata(
                    annotation.name(),
                    annotation.description(),
                    annotation.category()
            );
        }

        // 无注解时使用默认命名
        String simpleName = toolClass.getSimpleName();
        String name = simpleName.replace("Tool", "");
        return new ToolMetadata(name, "未标注描述的 Java 工具", "SYSTEM");
    }

    /**
     * 序列化构造参数
     */
    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(args);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("参数无法序列化: " + e.getMessage(), e);
        }
    }

    // ==================== 实例创建辅助方法 ====================

    /**
     * 创建工具实例（支持参数类型转换）
     */
    private Object createInstance(Class<?> toolClass, Object[] args) throws Exception {
        if (args == null || args.length == 0) {
            return toolClass.getDeclaredConstructor().newInstance();
        }

        Exception lastException = null;
        for (var constructor : toolClass.getConstructors()) {
            if (constructor.getParameterCount() == args.length) {
                try {
                    Object[] convertedArgs = convertArgs(args, constructor.getParameterTypes());
                    return constructor.newInstance(convertedArgs);
                } catch (Exception e) {
                    lastException = e;
                    log.debug("Constructor {} failed: {}", constructor, e.getMessage());
                }
            }
        }

        throw new NoSuchMethodException(
                "No matching constructor found for " + toolClass.getName() +
                        ". Args: " + Arrays.toString(args) +
                        ". Last error: " + (lastException != null ? lastException.getMessage() : "none")
        );
    }

    /**
     * 转换参数类型以匹配构造函数
     */
    private Object[] convertArgs(Object[] args, Class<?>[] paramTypes) {
        Object[] result = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = convertArg(args[i], paramTypes[i]);
        }
        return result;
    }

    /**
     * 单个参数类型转换
     */
    private Object convertArg(Object arg, Class<?> targetType) {
        if (arg == null) return null;

        Class<?> argType = arg.getClass();

        // 类型已兼容
        if (targetType.isAssignableFrom(argType)) {
            return arg;
        }

        // Number 类型转换
        if (arg instanceof Number num) {
            return convertNumber(num, targetType);
        }

        // Boolean 类型转换
        if (arg instanceof Boolean bool && (targetType == boolean.class || targetType == Boolean.class)) {
            return bool;
        }

        // String 类型
        if (arg instanceof String str && targetType == String.class) {
            return str;
        }

        throw new IllegalArgumentException("Cannot convert " + argType + " to " + targetType);
    }

    /**
     * Number 类型之间的转换
     */
    private Object convertNumber(Number num, Class<?> targetType) {
        if (targetType == int.class || targetType == Integer.class) {
            return num.intValue();
        } else if (targetType == long.class || targetType == Long.class) {
            return num.longValue();
        } else if (targetType == double.class || targetType == Double.class) {
            return num.doubleValue();
        } else if (targetType == float.class || targetType == Float.class) {
            return num.floatValue();
        } else if (targetType == short.class || targetType == Short.class) {
            return num.shortValue();
        } else if (targetType == byte.class || targetType == Byte.class) {
            return num.byteValue();
        }
        return num;
    }

    // ==================== 内部记录类 ====================

    /**
     * 工具元数据记录
     */
    private record ToolMetadata(String name, String description, String category) {
    }
}
