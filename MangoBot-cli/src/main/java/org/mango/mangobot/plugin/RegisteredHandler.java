package org.mango.mangobot.plugin;

import lombok.Data;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

@Data
public class RegisteredHandler {
    private final Method method;
    private final Object handlerInstance;
    private final List<Annotation> annotations;
    private final int priority;
}