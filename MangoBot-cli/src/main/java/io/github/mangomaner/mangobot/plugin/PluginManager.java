package io.github.mangomaner.mangobot.plugin;

import io.github.mangomaner.mangobot.annotation.*;
import io.github.mangomaner.mangobot.manager.event.MangoEventPublisher;
import io.github.mangomaner.mangobot.plugin.register.web.MangoArgumentResolvers;
import io.github.mangomaner.mangobot.plugin.register.web.MangoReturnValueHandler;
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
import java.lang.reflect.Method;
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
    private ConfigurableApplicationContext applicationContext;

    @Resource
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Resource
    private RequestMappingHandlerAdapter requestMappingHandlerAdapter;

    private final List<Plugin> plugins = new ArrayList<>();
    private final Map<String, PluginClassLoader> classLoaders = new HashMap<>();
    private final String PLUGIN_DIR = "plugins";

    // 记录动态注册的Controller BeanName -> 路由信息列表，用于卸载
    private final Map<String, List<RequestMappingInfo>> pluginControllerMappings = new ConcurrentHashMap<>();
    // 记录动态注册的Bean Name
    private final List<String> registeredBeanNames = new ArrayList<>();

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
    }

    private void loadPlugin(File jarFile) {
        try {
            PluginClassLoader loader = PluginClassLoader.create(jarFile, getClass().getClassLoader());
            JarFile jar = new JarFile(jarFile);
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName()
                            .replace("/", ".")
                            .replace(".class", "");

                    if (entry.getName().equals("module-info.class") ||
                            entry.getName().startsWith("META-INF/versions/") && entry.getName().endsWith("/module-info.class")) {
                        log.info("跳过模块描述符: " + entry.getName());
                        continue;
                    }
                    try {
                        // 加载类的元信息
                        Class<?> clazz = loader.loadClass(className);
                        // 若遇到继承了插件接口的类，则直接实例化
                        if (Plugin.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
                            Plugin plugin = (Plugin) clazz.getDeclaredConstructor().newInstance();
                            PluginContext context = new PluginContext(applicationContext);
                            plugin.onEnable(context);
                            plugins.add(plugin);
                            classLoaders.put(clazz.getName(), loader);
                            log.info("已加载插件: " + clazz.getName());
                        }

                        // 处理 @MangoBot 注解
                        if (clazz.isAnnotationPresent(MangoBot.class)) {
                            registerHandlers(clazz.getDeclaredConstructor().newInstance());
                        }

                        // 处理 Web Controller ( @MangoBotController)
                        if (clazz.isAnnotationPresent(MangoBotController.class)) {

                            registerController(clazz);

                            // 记录 ClassLoader
                            if (!classLoaders.containsKey(clazz.getName())) {
                                classLoaders.put(clazz.getName(), loader);
                            }
                        }

                    } catch (Exception e) {
                        log.warn("加载类 {} 失败: {}", className, e.toString());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void registerController(Class<?> clazz) {
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
            registeredBeanNames.add(beanName);

            // 2. 获取 Bean 实例
            Object bean = applicationContext.getBean(beanName);

            // 3. 解析并注册 RequestMapping
            List<RequestMappingInfo> registeredMappings = new ArrayList<>();
            Method[] methods = clazz.getDeclaredMethods();
            
            for (Method method : methods) {
                RequestMappingInfo mappingInfo = null;

                try {
                    // 解析自定义注解
                    if (clazz.isAnnotationPresent(MangoBotController.class) || method.isAnnotationPresent(MangoBotRequestMapping.class)) {
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

    private void registerHandlers(Object instance) {
        Method[] methods = instance.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(MangoBotEventListener.class)) {
                eventPublisher.registerListener(method, instance);
                // log.debug("注册插件 listener: {}", method.getName());
            }
        }
    }

}
//public void unloadPlugins() {
//        // 1. 卸载插件
//        plugins.forEach(Plugin::onDisable);
//        plugins.clear();
//
//        // 2. 卸载动态注册的 Controller
//        new ArrayList<>(registeredBeanNames).forEach(this::unregisterController);
//        registeredBeanNames.clear();
//
//        // 3. 关闭 ClassLoader
//        classLoaders.forEach((name, loader) -> {
//            try {
//                loader.close();
//            } catch (IOException ignored) {}
//        });
//        classLoaders.clear();
//
//        log.info("所有插件已卸载");
//    }