package org.mango.mangobot.plugin;

import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MessageDispatcher {

    // 支持多种消息类型的处理器映射表
    private final Map<Class<?>, List<RegisteredHandler>> handlers = new ConcurrentHashMap<>();

    /**
     * 注册一个带注解的方法为消息处理器
     */
    public void registerHandlerMethod(Object instance, Method method) {
        Arrays.stream(method.getAnnotations())
                .filter(annotation -> annotation.annotationType().getName().startsWith("org.mango.mangobot.annotation.QQ.method"))
                .forEach(annotation -> {
                    Class<?> annotationType = annotation.annotationType();
                    addHandler(annotationType, new RegisteredHandler(instance, method));
                });
    }

    /**
     * 添加一个处理器到指定消息类型下
     */
    private void addHandler(Class<?> annotationType, RegisteredHandler handler) {
        handlers.computeIfAbsent(annotationType, k -> new ArrayList<>()).add(handler);
    }

    /**
     * 分发某种类型的消息给所有匹配的处理器
     */
    public void dispatchMessage(Class<?> messageType, Object... args) {
        List<RegisteredHandler> list = handlers.get(messageType);
        if (list != null) {
            for (RegisteredHandler handler : list) {
                try {
                    handler.invoke(args);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 内部类：封装处理器方法和实例
     */
    public static class RegisteredHandler {
        private final Object instance;
        private final Method method;

        public RegisteredHandler(Object instance, Method method) {
            this.instance = instance;
            this.method = method;
        }

        public void invoke(Object... args) throws InvocationTargetException, IllegalAccessException {
            method.invoke(instance, args);
        }
    }
}