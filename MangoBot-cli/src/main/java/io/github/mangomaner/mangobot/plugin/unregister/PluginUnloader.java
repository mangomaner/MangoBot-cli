package io.github.mangomaner.mangobot.plugin.unregister;

import io.github.mangomaner.mangobot.manager.event.MangoEventPublisher;
import io.github.mangomaner.mangobot.plugin.PluginRuntimeWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
public class PluginUnloader {

    @Resource
    private MangoEventPublisher eventPublisher;

    @Resource
    private ConfigurableApplicationContext applicationContext;

    @Resource
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    public void unload(PluginRuntimeWrapper wrapper) {
        String pluginId = wrapper.getId();

        try {
            // 1. 调用 onDisable
            if (wrapper.getPluginInstance() != null) {
                try {
                    wrapper.getPluginInstance().onDisable();
                } catch (Exception e) {
                    log.error("插件 {} onDisable 执行出错", pluginId, e);
                }
            }

            // 2. 注销 Controller
            List<String> beanNames = wrapper.getControllerBeanNames();
            for (String beanName : beanNames) {
                List<RequestMappingInfo> mappings = wrapper.getControllerMappings().get(beanName);
                unregisterController(beanName, mappings);
            }

            // 3. 注销监听器
            if (!wrapper.getListenerInstances().isEmpty()) {
                wrapper.getListenerInstances().forEach(instance -> eventPublisher.unregisterListener(instance));
            }

            // 4. 关闭 ClassLoader
            try {
                wrapper.getClassLoader().close();
            } catch (IOException e) {
                log.warn("关闭插件 {} ClassLoader 失败", pluginId, e);
            }

            log.info("插件 {} 已卸载", pluginId);

        } catch (Exception e) {
            log.error("卸载插件 {} 失败", pluginId, e);
        }
    }

    private void unregisterController(String beanName, List<RequestMappingInfo> mappings) {
        try {
            // 1. 注销 RequestMapping
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
}
