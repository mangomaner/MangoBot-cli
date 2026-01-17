package io.github.mangomaner.mangobot.plugin.register;

import io.github.mangomaner.mangobot.annotation.MangoBotApiService;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.annotation.web.MangoBotRequestMapping;
import io.github.mangomaner.mangobot.annotation.web.MangoRequestMethod;
import io.github.mangomaner.mangobot.manager.event.MangoEventPublisher;
import io.github.mangomaner.mangobot.plugin.PluginRuntimeWrapper;
import io.github.mangomaner.mangobot.plugin.register.web.MangoArgumentResolvers;
import io.github.mangomaner.mangobot.plugin.register.web.MangoReturnValueHandler;
import io.github.mangomaner.mangobot.service.OneBotApiService;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@Slf4j
public class PluginRegistrar {

    @Resource
    private MangoEventPublisher eventPublisher;

    @Resource
    private ConfigurableApplicationContext applicationContext;

    @Resource
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Resource
    private RequestMappingHandlerAdapter requestMappingHandlerAdapter;

    @Resource
    private OneBotApiService oneBotApiService;

    /**
     * 注册 Web 扩展组件 (ArgumentResolvers, ReturnValueHandlers)
     */
    public void registerWebComponents() {
        try {
            List<HttpMessageConverter<?>> converters = requestMappingHandlerAdapter.getMessageConverters();
            ConfigurableBeanFactory beanFactory = applicationContext.getBeanFactory();

            // 注册 ArgumentResolvers
            List<HandlerMethodArgumentResolver> argumentResolvers = new ArrayList<>(
                    Objects.requireNonNull(requestMappingHandlerAdapter.getArgumentResolvers()));

            argumentResolvers.add(0, new MangoArgumentResolvers.RequestBodyResolver(converters));
            argumentResolvers.add(0, new MangoArgumentResolvers.PathVariableResolver(beanFactory));
            argumentResolvers.add(0, new MangoArgumentResolvers.RequestParamResolver(beanFactory));

            requestMappingHandlerAdapter.setArgumentResolvers(argumentResolvers);
            log.debug("已注册 MangoArgumentResolvers");

            // 注册 ReturnValueHandler
            List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>(
                    Objects.requireNonNull(requestMappingHandlerAdapter.getReturnValueHandlers()));

            MangoReturnValueHandler mangoHandler = new MangoReturnValueHandler(converters);
            handlers.add(0, mangoHandler);
            requestMappingHandlerAdapter.setReturnValueHandlers(handlers);
            log.debug("已注册 MangoReturnValueHandler");

        } catch (Exception e) {
            log.error("注册 MangoBot Web 扩展组件失败", e);
        }
    }

    /**
     * 注册 Controller
     */
    public void registerController(Class<?> clazz, PluginRuntimeWrapper wrapper) {
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
            wrapper.getControllerBeanNames().add(beanName);

            // 2. 获取 Bean 实例
            Object bean = applicationContext.getBean(beanName);

            // 3. 解析并注册 RequestMapping
            List<RequestMappingInfo> registeredMappings = new ArrayList<>();
            Method[] methods = clazz.getDeclaredMethods();

            for (Method method : methods) {
                RequestMappingInfo mappingInfo = null;

                try {
                    if (method.isAnnotationPresent(MangoBotRequestMapping.class)) {
                        mappingInfo = parseMangoMapping(method, clazz);
                    }

                    if (mappingInfo != null) {
                        requestMappingHandlerMapping.registerMapping(mappingInfo, bean, method);
                        registeredMappings.add(mappingInfo);
                        log.debug("注册插件路由: {} -> {}", mappingInfo, method.getName());
                    }
                } catch (Exception e) {
                    log.error("注册方法路由失败: {}", method.getName(), e);
                }
            }

            if (!registeredMappings.isEmpty()) {
                wrapper.getControllerMappings().put(beanName, registeredMappings);
            }

            log.debug("已注册 Controller: {}", beanName);

        } catch (Exception e) {
            log.error("注册 Controller {} 失败", beanName, e);
            // 注册失败时尝试清理
            unregisterBean(beanName);
        }
    }

    private void unregisterBean(String beanName) {
        try {
            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getBeanFactory();
            if (beanFactory.containsBeanDefinition(beanName)) {
                beanFactory.removeBeanDefinition(beanName);
            }
        } catch (Exception e) {
            log.error("清理 Bean {} 失败", beanName, e);
        }
    }

    /**
     * 注入 API 服务
     */
    public void injectApiServices(Class<?> clazz, Object instance) {
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

    /**
     * 注册事件监听器
     */
    public void registerEventListeners(Class<?> clazz, Object instance, PluginRuntimeWrapper wrapper) {
        boolean hasListener = false;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(MangoBotEventListener.class)) {
                eventPublisher.registerListener(method, instance);
                hasListener = true;
            }
        }
        if (hasListener && !wrapper.getListenerInstances().contains(instance)) {
            wrapper.getListenerInstances().add(instance);
        }
    }

    private RequestMappingInfo parseMangoMapping(Method method, Class<?> handlerType) {
        MangoBotRequestMapping methodAnn = method.getAnnotation(MangoBotRequestMapping.class);
        if (methodAnn == null) return null;

        RequestMappingInfo.Builder builder = RequestMappingInfo.paths(methodAnn.value());
        RequestMappingInfo methodInfo = builder.methods(mapMethod(methodAnn.method())).build();

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
}
