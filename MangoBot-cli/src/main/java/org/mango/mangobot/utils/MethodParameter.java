package org.mango.mangobot.utils;

import java.lang.annotation.Annotation;

public class MethodParameter {
    private final Class<?> parameterType;
    private final Annotation[] annotations;
    private final int index;

    public MethodParameter(Class<?> parameterType, Annotation[] annotations, int index) {
        this.parameterType = parameterType;
        this.annotations = annotations;
        this.index = index;
    }

    public Class<?> getParameterType() {
        return parameterType;
    }

    public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType() == annotationClass) {
                return annotationClass.cast(annotation);
            }
        }
        return null;
    }

    public boolean hasAnnotation(Class<? extends Annotation> annotationClass) {
        return getAnnotation(annotationClass) != null;
    }

    public int getIndex() {
        return index;
    }
}