package org.mango.mangobot.plugin;

import lombok.Data;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

@Data
public class RegisteredHandler {
    // 注册的方法
    private final Method method;
    // 方法所在的类
    private final Object handlerInstance;
    // 方法的注解
    private final List<Annotation> annotations;
    // 方法的优先级
    private final int priority;
    //
    private final Set<String> segmentTypes;
}