package io.github.mangomaner.mangobot.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 获取请求体，类似于 Spring 的 @RequestBody
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface MangoBotRequestBody {
    boolean required() default true;
}
