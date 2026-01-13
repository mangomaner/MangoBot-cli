package org.mango.mangobot.manager.event;

import lombok.extern.slf4j.Slf4j;
import org.mango.mangobot.annotation.MangoBotEventListener;
import org.mango.mangobot.annotation.MangoBotHandler;
import org.mango.mangobot.annotation.PluginPriority;
import org.mango.mangobot.model.onebot.event.Event;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class MangoEventPublisher {

    private final Map<Class<?>, List<ListenerMethod>> listenerCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> handlerInstances = new ConcurrentHashMap<>();
    private ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 10, 1000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new ThreadPoolExecutor.CallerRunsPolicy());

    // 无参构造函数，用于 Spring 或手动实例化
    public MangoEventPublisher() {
        initListeners("org.mango.mangobot.handler");
    }

    public void initListeners(String packageToScan) {
        listenerCache.clear();
        handlerInstances.clear();

        try {
            // 获取指定包中的所有类并遍历
            List<Class<?>> classes = getClasses(packageToScan);
            for (Class<?> clazz : classes) {
                // 检查类是否被 @MangoBotHandler 注解
                if (clazz.isAnnotationPresent(MangoBotHandler.class)){
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(MangoBotEventListener.class)) {
                            // 创建或获取处理程序类的实例
                            Class<?> handlerClass = method.getDeclaringClass();
                            Object handlerInstance = handlerInstances.computeIfAbsent(handlerClass, k -> {
                                try {
                                    return k.getDeclaredConstructor().newInstance();
                                } catch (Exception e) {
                                    log.error("实例化处理程序类失败: {}", k.getName(), e);
                                    return null;
                                }
                            });
                            if (handlerInstance != null) {
                                registerListener(method, handlerInstance);
                            }
                        }
                    }
                }

            }
        } catch (Exception e) {
            log.error("扫描包失败: {}", packageToScan, e);
        }
        
        // 按优先级排序监听器
        listenerCache.values().forEach(list -> Collections.sort(list));
        log.info("主程序 MangoEventPublisher 初始化完成，从包 '{}' 中加载了 {} 种事件类型。", packageToScan, listenerCache.size());
    }

    /**
     * 注册监听器
     * @param method
     * @param instance
     */
    public void registerListener(Method method, Object instance) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 1) {
            log.warn("使用 @MangoBotEventListener 注解的方法 {} 必须有且仅有一个参数。", method.getName());
            return;
        }

        if (method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class) {
            throw new IllegalStateException("使用 @MangoBotEventListener 注解的方法 " + method.getName() + " 必须返回 boolean/Boolean 类型。");
        }

        Class<?> eventType = parameterTypes[0];
        if (!Event.class.isAssignableFrom(eventType)) {
            log.warn("方法 {} 的参数必须是 Event 的子类。", method.getName());
            return;
        }

        int priority = 10;
        if (method.isAnnotationPresent(PluginPriority.class)) {
            priority = method.getAnnotation(PluginPriority.class).value();
        }

        ListenerMethod listenerMethod = new ListenerMethod(instance, method, priority);
        listenerCache.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listenerMethod);
        // 添加新监听器后重新排序
        Collections.sort(listenerCache.get(eventType));

        log.info("已注册监听器: {} 用于事件: {}", method.getName(), eventType.getSimpleName());
    }

    /**
     * 多线程发布事件
     * @param event
     */
    public void publish(Event event) {
        executor.execute(() -> {
            List<ListenerMethod> listeners = getListenersForEvent(event.getClass());
            for (ListenerMethod listener : listeners) {
                try {
                    boolean result = (boolean) listener.method.invoke(listener.bean, event);
                    if (!result) {
                        log.debug("监听器 {} 返回 false，停止传播。", listener.method.getName());
                        break;
                    }
                } catch (Exception e) {
                    log.error("执行监听器时出错: {}", listener.method.getName(), e);
                }
            }
        });
    }

    /**
     * 使用 Spring 的类扫描机制获取指定包中的所有类
     * 支持 JAR 包和文件系统
     */
    private List<Class<?>> getClasses(String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(MangoBotHandler.class));
        
        Set<BeanDefinition> candidates = scanner.findCandidateComponents(packageName);
        
        for (BeanDefinition candidate : candidates) {
            try {
                Class<?> clazz = Class.forName(candidate.getBeanClassName());
                classes.add(clazz);
            } catch (ClassNotFoundException e) {
                log.error("加载类失败: {}", candidate.getBeanClassName(), e);
            }
        }
        
        return classes;
    }

    private List<ListenerMethod> getListenersForEvent(Class<?> eventClass) {
        List<ListenerMethod> result = new ArrayList<>();
        
        for (Map.Entry<Class<?>, List<ListenerMethod>> entry : listenerCache.entrySet()) {
            if (entry.getKey().isAssignableFrom(eventClass)) {
                result.addAll(entry.getValue());
            }
        }
        
        Collections.sort(result);
        return result;
    }

    private static class ListenerMethod implements Comparable<ListenerMethod> {
        final Object bean;
        final Method method;
        final int priority;

        public ListenerMethod(Object bean, Method method, int priority) {
            this.bean = bean;
            this.method = method;
            this.priority = priority;
            this.method.setAccessible(true);
        }

        @Override
        public int compareTo(ListenerMethod o) {
            return Integer.compare(this.priority, o.priority);
        }
    }
}
