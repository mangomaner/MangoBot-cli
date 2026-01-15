package io.github.mangomaner.mangobot.plugin;

import io.github.mangomaner.mangobot.annotation.*;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotApiService;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.annotation.web.MangoBotRequestMapping;
import io.github.mangomaner.mangobot.annotation.web.MangoRequestMethod;
import io.github.mangomaner.mangobot.manager.event.MangoEventPublisher;
import io.github.mangomaner.mangobot.plugin.register.web.MangoArgumentResolvers;
import io.github.mangomaner.mangobot.plugin.register.web.MangoReturnValueHandler;
import io.github.mangomaner.mangobot.service.OneBotApiService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Component
@Slf4j
public class PluginManager {
    @Resource
    private MangoEventPublisher eventPublisher;

    @Resource
    private OneBotApiService oneBotApiService;

    @Resource
    private ConfigurableApplicationContext applicationContext;

    @Resource
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Resource
    private RequestMappingHandlerAdapter requestMappingHandlerAdapter;

    // 替代原有的分散存储，统一管理插件资源
    private final Map<String, PluginRuntimeWrapper> pluginRegistry = new ConcurrentHashMap<>();
    private final String PLUGIN_DIR = "plugins";

    // 记录动态注册的Controller BeanName -> 路由信息列表，用于卸载
    private final Map<String, List<RequestMappingInfo>> pluginControllerMappings = new ConcurrentHashMap<>();

    private Method getMappingForMethod;

    @PostConstruct
    public void init() {
        // 1. 初始化 RequestMapping 解析方法
        try {
            Class<?> superClass = RequestMappingHandlerMapping.class;
            while (superClass != Object.class) {
                try {
                    getMappingForMethod = superClass.getDeclaredMethod("getMappingForMethod", Method.class, Class.class);
                    getMappingForMethod.setAccessible(true);
                    break;
                } catch (NoSuchMethodException e) {
                    superClass = superClass.getSuperclass();
                }
            }
        } catch (Exception e) {
            log.error("初始化 PluginManager 失败: 无法获取 getMappingForMethod 方法", e);
        }

        // 2. 注册自定义 ReturnValueHandler 和 ArgumentResolvers
        try {
            List<HttpMessageConverter<?>> converters = requestMappingHandlerAdapter.getMessageConverters();
            ConfigurableBeanFactory beanFactory = applicationContext.getBeanFactory();

            // 注册 ArgumentResolvers
            List<HandlerMethodArgumentResolver> argumentResolvers = new ArrayList<>(
                    Objects.requireNonNull(requestMappingHandlerAdapter.getArgumentResolvers()));
            
            // 添加到头部，优先解析
            argumentResolvers.add(0, new MangoArgumentResolvers.RequestBodyResolver(converters));
            argumentResolvers.add(0, new MangoArgumentResolvers.PathVariableResolver(beanFactory));
            argumentResolvers.add(0, new MangoArgumentResolvers.RequestParamResolver(beanFactory));
            
            requestMappingHandlerAdapter.setArgumentResolvers(argumentResolvers);
            log.info("已注册 MangoArgumentResolvers");

            // 注册 ReturnValueHandler
            List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>(
                    Objects.requireNonNull(requestMappingHandlerAdapter.getReturnValueHandlers()));
            
            MangoReturnValueHandler mangoHandler = new MangoReturnValueHandler(converters);
            handlers.add(0, mangoHandler);
            requestMappingHandlerAdapter.setReturnValueHandlers(handlers);
            log.info("已注册 MangoReturnValueHandler");

        } catch (Exception e) {
            log.error("注册 MangoBot Web 扩展组件失败", e);
        }
    }

    public void loadPlugins() {
        File dir = new File(PLUGIN_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("插件目录不存在");
            return;
        }

        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.getName().endsWith(".jar")) {
                loadPlugin(file);
            }
        }

        eventPublisher.printAllListeners();
    }

    public void loadPlugin(File jarFile) {
        String pluginId = jarFile.getName();
        if (pluginRegistry.containsKey(pluginId)) {
            log.warn("插件 {} 已加载，跳过", pluginId);
            return;
        }

        PluginClassLoader loader = null;
        try {
            loader = PluginClassLoader.create(jarFile, getClass().getClassLoader());
            PluginRuntimeWrapper wrapper = new PluginRuntimeWrapper(pluginId, loader);

            // 使用 try-with-resources 确保 JarFile 在遍历后立即关闭
            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class")) {
                        String className = entry.getName()
                                .replace("/", ".")
                                .replace(".class", "");

                        if (entry.getName().equals("module-info.class") ||
                                entry.getName().startsWith("META-INF/versions/") && entry.getName().endsWith("/module-info.class")) {
                            continue;
                        }
                        try {
                            Class<?> clazz = loader.loadClass(className);

                            boolean isPlugin = Plugin.class.isAssignableFrom(clazz) && !clazz.isInterface();
                            boolean hasMangoBot = clazz.isAnnotationPresent(MangoBot.class);
                            boolean isRequestMapping = clazz.isAnnotationPresent(MangoBotRequestMapping.class);

                            Object instance = null;

                            if (hasMangoBot) {
                                try {
                                    if (isRequestMapping) {
                                        registerController(clazz, wrapper.controllerBeanNames);
                                        String beanName = clazz.getName();
                                        instance = applicationContext.getBean(beanName);
                                    }

                                    if (isPlugin) {
                                        instance = instance == null ? clazz.getDeclaredConstructor().newInstance() : instance;
                                        Plugin plugin = (Plugin) instance;
                                        wrapper.pluginInstance = plugin;

                                        PluginContext context = new PluginContext(applicationContext);

                                        registerEventListeners(clazz, instance, wrapper.listenerInstances);
                                        injectApiServices(clazz, instance);

                                        plugin.onEnable(context);
                                        log.info("已加载插件主类: {}", clazz.getName());
                                    } else {
                                        instance = instance == null ? clazz.getDeclaredConstructor().newInstance(): instance;
                                        registerEventListeners(clazz, instance, wrapper.listenerInstances);
                                        injectApiServices(clazz, instance);
                                    }

                                } catch (ReflectiveOperationException e) {
                                    log.warn("加载或初始化类 {} 失败", className, e);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("加载类 {} 失败: {}", className, e.toString());
                        }
                    }
                }
            }
            
            pluginRegistry.put(pluginId, wrapper);
            log.info("插件 {} 加载完成", pluginId);
        } catch (Exception e) {
            log.error("加载插件 {} 失败", jarFile.getName(), e);
            // 如果加载失败，确保关闭 ClassLoader 以释放文件句柄
            if (loader != null) {
                try {
                    loader.close();
                } catch (IOException ex) {
                    log.warn("关闭加载失败的插件 ClassLoader 出错", ex);
                }
            }
        }
    }

    /**
     * 注册所有带 @MangoBotEventListener 注解的方法
     */
    private void registerEventListeners(Class<?> clazz, Object instance, List<Object> listenerInstances) {
        boolean hasListener = false;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(MangoBotEventListener.class)) {
                eventPublisher.registerListener(method, instance);
                hasListener = true;
            }
        }
        if (hasListener && !listenerInstances.contains(instance)) {
            listenerInstances.add(instance);
        }
    }

    /**
     * 为所有带 @MangoBotApiService 注解的字段注入 oneBotApiService
     */
    private void injectApiServices(Class<?> clazz, Object instance) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(MangoBotApiService.class)) {
                field.setAccessible(true);
                try {
                    field.set(instance, oneBotApiService);
                } catch (IllegalAccessException e) {
                    log.error("注入 MangoBotApiService 到 {}.{} 失败",
                            clazz.getSimpleName(), field.getName(), e);
                }
            }
        }
    }

    private void registerController(Class<?> clazz, List<String> registeredBeans) {
        String beanName = clazz.getName();
        if (applicationContext.containsBean(beanName)) {
            log.warn("Bean {} 已存在，跳过注册", beanName);
            return;
        }

        try {
            // 1. 注册 BeanDefinition
            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getBeanFactory();
            BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
            beanFactory.registerBeanDefinition(beanName, beanDefinitionBuilder.getBeanDefinition());
            registeredBeans.add(beanName);

            // 2. 获取 Bean 实例
            Object bean = applicationContext.getBean(beanName);

            // 3. 解析并注册 RequestMapping
            List<RequestMappingInfo> registeredMappings = new ArrayList<>();
            Method[] methods = clazz.getDeclaredMethods();
            
            for (Method method : methods) {
                RequestMappingInfo mappingInfo = null;

                try {
                    // 解析自定义注解
                    if (method.isAnnotationPresent(MangoBotRequestMapping.class)) {
                        mappingInfo = parseMangoMapping(method, clazz);
                    }

                    if (mappingInfo != null) {
                        requestMappingHandlerMapping.registerMapping(mappingInfo, bean, method);
                        registeredMappings.add(mappingInfo);
                        log.info("注册插件路由: {} -> {}", mappingInfo, method.getName());
                    }
                } catch (Exception e) {
                    log.error("注册方法路由失败: {}", method.getName(), e);
                }
            }

            if (!registeredMappings.isEmpty()) {
                pluginControllerMappings.put(beanName, registeredMappings);
            }

            log.info("已注册 Controller: {}", beanName);

        } catch (Exception e) {
            log.error("注册 Controller {} 失败", beanName, e);
            unregisterController(beanName);
        }
    }

    private RequestMappingInfo parseMangoMapping(Method method, Class<?> handlerType) {
        MangoBotRequestMapping methodAnn = method.getAnnotation(MangoBotRequestMapping.class);
        if (methodAnn == null) return null;

        RequestMappingInfo.Builder builder = RequestMappingInfo.paths(methodAnn.value());

        RequestMappingInfo methodInfo = builder.methods(mapMethod(methodAnn.method())).build();

        // 处理类级别的注解
        MangoBotRequestMapping typeAnn = handlerType.getAnnotation(MangoBotRequestMapping.class);
        if (typeAnn != null) {
            RequestMappingInfo typeInfo = RequestMappingInfo.paths(typeAnn.value()).build();
            return typeInfo.combine(methodInfo);
        }

        return methodInfo;
    }

    private RequestMethod mapMethod(MangoRequestMethod method) {
        if (method == null) return null;
        switch (method) {
            case GET: return RequestMethod.GET;
            case POST: return RequestMethod.POST;
            case PUT: return RequestMethod.PUT;
            case DELETE: return RequestMethod.DELETE;
            default: return null; 
        }
    }

    private void unregisterController(String beanName) {
        try {
            // 1. 注销 RequestMapping
            List<RequestMappingInfo> mappings = pluginControllerMappings.remove(beanName);
            if (mappings != null) {
                for (RequestMappingInfo mapping : mappings) {
                    requestMappingHandlerMapping.unregisterMapping(mapping);
                }
            }

            // 2. 移除 BeanDefinition
            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getBeanFactory();
            if (beanFactory.containsBeanDefinition(beanName)) {
                beanFactory.removeBeanDefinition(beanName);
                log.info("已卸载 Controller: {}", beanName);
            }
        } catch (Exception e) {
            log.error("卸载 Controller {} 失败", beanName, e);
        }
    }
    /**
     * 卸载所有插件
     */
    public void unloadPlugins() {
        if (pluginRegistry.isEmpty()) {
            return;
        }
        
        // 创建副本进行遍历，避免并发修改异常
        new ArrayList<>(pluginRegistry.keySet()).forEach(this::unloadPlugin);
        
        pluginRegistry.clear();
        log.info("所有插件已卸载");
    }

    /**
     * 卸载指定插件
     * @param pluginId 插件ID (通常是Jar文件名)
     */
    public void unloadPlugin(String pluginId) {
        PluginRuntimeWrapper wrapper = pluginRegistry.remove(pluginId);
        if (wrapper == null) {
            log.warn("尝试卸载不存在的插件: {}", pluginId);
            return;
        }

        try {
            // 1. 调用 onDisable
            if (wrapper.pluginInstance != null) {
                try {
                    wrapper.pluginInstance.onDisable();
                } catch (Exception e) {
                    log.error("插件 {} onDisable 执行出错", pluginId, e);
                }
            }

            // 2. 注销 Controller
            if (!wrapper.controllerBeanNames.isEmpty()) {
                wrapper.controllerBeanNames.forEach(this::unregisterController);
            }

            // 3. 注销监听器
            if (!wrapper.listenerInstances.isEmpty()) {
                wrapper.listenerInstances.forEach(instance -> eventPublisher.unregisterListener(instance));
            }

            // 4. 关闭 ClassLoader
            try {
                wrapper.classLoader.close();
            } catch (IOException e) {
                log.warn("关闭插件 {} ClassLoader 失败", pluginId, e);
            }

            log.info("插件 {} 已卸载", pluginId);

        } catch (Exception e) {
            log.error("卸载插件 {} 失败", pluginId, e);
        }
    }

    /**
     * 插件运行时包装类，持有插件相关的所有资源
     */
    private static class PluginRuntimeWrapper {
        final String id;
        final PluginClassLoader classLoader;
        Plugin pluginInstance;
        final List<String> controllerBeanNames = new ArrayList<>();
        final List<Object> listenerInstances = new ArrayList<>();

        public PluginRuntimeWrapper(String id, PluginClassLoader classLoader) {
            this.id = id;
            this.classLoader = classLoader;
        }
    }
}
