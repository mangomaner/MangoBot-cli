package io.github.mangomaner.mangobot.api;

import io.github.mangomaner.mangobot.module.agent.capability.tool.ToolRegistrationService;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.plugin.core.PluginClassLoader;

import java.util.List;
import java.util.function.Supplier;

/**
 * Agent 工具 API (静态工具类)
 *
 * <p>提供工具注册和注销能力，供插件和主程序使用。
 *
 * <h3>插件自动识别</h3>
 * <p>当插件调用此 API 时，会自动检测调用者所属的插件 ID，
 * 无需手动传入。检测机制：
 * <ol>
 *   <li>优先检查当前线程的 ContextClassLoader</li>
 *   <li>其次分析调用栈中所有类的 ClassLoader</li>
 * </ol>
 *
 * <h3>注册方式</h3>
 * <ul>
 *   <li>{@link #registerTool(Class)} - 注册工具类（无参数，支持所有来源）</li>
 *   <li>{@link #registerTool(Class, List)} - 注册工具类（无参数，指定支持的来源列表）</li>
 *   <li>{@link #registerTool(Class, Object...)} - 注册工具类（带构造参数，支持所有来源）</li>
 *   <li>{@link #registerTool(Class, List, Object...)} - 注册工具类（带构造参数，指定支持的来源列表）</li>
 *   <li>{@link #registerToolInstance(Object)} - 注册工具实例（全局共享，支持所有来源）</li>
 *   <li>{@link #registerToolInstance(Object, List)} - 注册工具实例（指定支持的来源列表）</li>
 *   <li>{@link #registerToolFactory(Class, Supplier)} - 注册工具工厂（每次新建，支持所有来源）</li>
 *   <li>{@link #registerToolFactory(Class, Supplier, List)} - 注册工具工厂（指定支持的来源列表）</li>
 * </ul>
 *
 * @see ToolRegistrationService
 */
public class MangoToolApi {

    private static ToolRegistrationService toolRegistrationService;

    private MangoToolApi() {}

    static void setService(ToolRegistrationService service) {
        MangoToolApi.toolRegistrationService = service;
    }

    private static void checkService() {
        if (toolRegistrationService == null) {
            throw new IllegalStateException("MangoToolApi has not been initialized yet.");
        }
    }

    /**
     * 自动检测当前调用者的插件 ID
     * 
     * @return 插件数据库 ID，如果非插件调用则返回 null
     */
    private static Integer detectPluginId() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader instanceof PluginClassLoader pcl) {
            Long id = pcl.getPluginDbId();
            return id != null ? id.intValue() : null;
        }
        
        return detectPluginIdFromStackTrace();
    }

    /**
     * 从调用栈中检测插件 ID
     */
    private static Integer detectPluginIdFromStackTrace() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        
        for (StackTraceElement element : stackTrace) {
            try {
                Class<?> clazz = Class.forName(element.getClassName());
                ClassLoader loader = clazz.getClassLoader();
                
                if (loader instanceof PluginClassLoader pcl) {
                    Long id = pcl.getPluginDbId();
                    if (id != null) {
                        return id.intValue();
                    }
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
        
        return null;
    }

    /**
     * 注册工具类（无参数，支持所有来源）
     *
     * <p>自动检测调用者是否为插件，如果是则关联插件 ID。
     * <p>默认支持所有来源（web、group、private）。
     *
     * @param toolClass 工具类（应带有 @MangoTool 注解）
     * @return 工具配置 ID
     */
    public static Integer registerTool(Class<?> toolClass) {
        checkService();
        Integer pluginId = detectPluginId();
        return toolRegistrationService.registerToolWithPluginId(toolClass, null, pluginId, null);
    }

    /**
     * 注册工具类（无参数，指定支持的来源列表）
     *
     * <p>自动检测调用者是否为插件，如果是则关联插件 ID。
     *
     * @param toolClass 工具类（应带有 @MangoTool 注解）
     * @param availableSources 支持的来源列表（如 [SessionSource.WEB, SessionSource.GROUP]）
     * @return 工具配置 ID
     */
    public static Integer registerTool(Class<?> toolClass, List<SessionSource> availableSources) {
        checkService();
        Integer pluginId = detectPluginId();
        return toolRegistrationService.registerToolWithPluginId(toolClass, null, pluginId, availableSources);
    }

    /**
     * 注册<b>内置</b>工具：新建那一次直接启用（已存在的行保持原样，用户关掉就是关掉）。
     *
     * <p>给产品自身的行为用——插件注册该走 {@link #registerTool(Class)} 那套默认禁用的接口。
     *
     * @param toolClass        工具类（应带有 @MangoTool 注解）
     * @param availableSources 支持的来源列表
     * @return 工具配置 ID
     */
    public static Integer registerBuiltinTool(Class<?> toolClass, List<SessionSource> availableSources) {
        checkService();
        return toolRegistrationService.registerBuiltinTool(toolClass, availableSources);
    }

    /**
     * 注册工具类（带构造参数，支持所有来源）
     *
     * <p>参数会自动序列化为 JSON 存储到数据库。
     * 支持基本类型：String、集合、Map 等可 JSON 序列化的类型。
     *
     * <p>自动检测调用者是否为插件，如果是则关联插件 ID。
     * <p>默认支持所有来源（web、group、private）。
     *
     * @param toolClass 工具类
     * @param args 构造参数（可变参数，自动序列化）
     * @return 工具配置 ID
     * @throws IllegalArgumentException 如果参数无法序列化
     */
    public static Integer registerTool(Class<?> toolClass, Object... args) {
        checkService();
        Integer pluginId = detectPluginId();
        return toolRegistrationService.registerToolWithPluginId(toolClass, args, pluginId, null);
    }

    /**
     * 注册工具类（带构造参数，指定支持的来源列表）
     *
     * <p>参数会自动序列化为 JSON 存储到数据库。
     * 支持基本类型：String、集合、Map 等可 JSON 序列化的类型。
     *
     * <p>自动检测调用者是否为插件，如果是则关联插件 ID。
     *
     * @param toolClass 工具类
     * @param availableSources 支持的来源列表（如 [SessionSource.WEB, SessionSource.GROUP]）
     * @param args 构造参数（可变参数，自动序列化）
     * @return 工具配置 ID
     * @throws IllegalArgumentException 如果参数无法序列化
     */
    public static Integer registerTool(Class<?> toolClass, List<SessionSource> availableSources, Object... args) {
        checkService();
        Integer pluginId = detectPluginId();
        return toolRegistrationService.registerToolWithPluginId(toolClass, args, pluginId, availableSources);
    }

    /**
     * 注册工具实例（直接传入实例，全局共享，支持所有来源）
     *
     * <p>自动检测调用者是否为插件，如果是则关联插件 ID。
     * <p>默认支持所有来源（web、group、private）。
     *
     * @param toolInstance 工具实例
     * @return 工具配置 ID
     */
    public static Integer registerToolInstance(Object toolInstance) {
        checkService();
        Integer pluginId = detectPluginId();
        return toolRegistrationService.registerToolInstance(toolInstance, pluginId);
    }

    /**
     * 注册工具实例（指定支持的来源列表）
     *
     * <p>直接传入工具实例，全局共享。
     * <p>自动检测调用者是否为插件，如果是则关联插件 ID。
     *
     * @param toolInstance 工具实例
     * @param availableSources 支持的来源列表（如 [SessionSource.WEB, SessionSource.GROUP]）
     * @return 工具配置 ID
     */
    public static Integer registerToolInstance(Object toolInstance, List<SessionSource> availableSources) {
        checkService();
        Integer pluginId = detectPluginId();
        return toolRegistrationService.registerToolInstanceWithSource(toolInstance, pluginId, availableSources);
    }

    /**
     * 注册工具工厂（每次创建 Agent 时新建实例，支持所有来源）
     *
     * <p>自动检测调用者是否为插件，如果是则关联插件 ID。
     * <p>默认支持所有来源（web、group、private）。
     *
     * @param toolClass 工具类
     * @param factory 工厂方法
     * @return 工具配置 ID
     */
    public static Integer registerToolFactory(Class<?> toolClass, Supplier<Object> factory) {
        checkService();
        Integer pluginId = detectPluginId();
        return toolRegistrationService.registerToolFactory(toolClass, factory, pluginId);
    }

    /**
     * 注册工具工厂（指定支持的来源列表）
     *
     * <p>每次创建 Agent 时调用工厂创建新实例。
     * <p>自动检测调用者是否为插件，如果是则关联插件 ID。
     *
     * @param toolClass 工具类
     * @param factory 工厂方法
     * @param availableSources 支持的来源列表（如 [SessionSource.WEB, SessionSource.GROUP]）
     * @return 工具配置 ID
     */
    public static Integer registerToolFactory(Class<?> toolClass, Supplier<Object> factory, List<SessionSource> availableSources) {
        checkService();
        Integer pluginId = detectPluginId();
        return toolRegistrationService.registerToolFactoryWithSource(toolClass, factory, pluginId, availableSources);
    }

    /**
     * 注销工具类
     *
     * @param toolClass 工具类
     */
    public static void unregisterTool(Class<?> toolClass) {
        checkService();
        toolRegistrationService.unregisterTool(toolClass.getName());
    }
}
