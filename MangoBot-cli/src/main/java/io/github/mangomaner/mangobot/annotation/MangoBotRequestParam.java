package io.github.mangomaner.mangobot.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 获取请求参数，类似于 Spring 的 @RequestParam
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface MangoBotRequestParam {
    /**
     * 参数名称
     */
    String value() default "";

    /**
     * 是否必须
     */
    boolean required() default true;

    /**
     * 默认值
     */
    String defaultValue() default "";
}
