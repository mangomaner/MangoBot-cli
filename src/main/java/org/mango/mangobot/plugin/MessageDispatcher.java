package org.mango.mangobot.plugin;

import org.mango.mangobot.QQ.method.TextMessage;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MessageDispatcher {

    private final Map<Class<?>, List<RegisteredHandler>> handlers = new HashMap<>();

    public void registerHandlerMethod(Object instance, Method method) {
        if (method.isAnnotationPresent(TextMessage.class)) {
            addHandler(TextMessage.class, new RegisteredHandler(instance, method));
        }
        // 其他注解也可以添加，比如 AtMessage、PokeMessage 等
    }

    private void addHandler(Class<?> annotationType, RegisteredHandler handler) {
        handlers.computeIfAbsent(annotationType, k -> new ArrayList<>()).add(handler);
    }

    public void dispatchTextMessage(String fromUser, String content) {
        List<RegisteredHandler> list = handlers.get(TextMessage.class);
        if (list != null) {
            for (RegisteredHandler handler : list) {
                try {
                    handler.invoke(fromUser, content);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

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