package io.github.mangomaner.mangobot.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标识请求路由映射。
 * 类似于 Spring 的 @RequestMapping。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MangoBotRequestMapping {
    /**
     * 请求路径
     */
    String value() default "";
    
    /**
     * 请求方法，默认为 GET
     */
    MangoRequestMethod method() default MangoRequestMethod.GET;
}
