package io.github.mangomaner.mangobot.annotation.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 获取 URI 路径变量，类似于 Spring 的 @PathVariable
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface MangoBotPathVariable {
    /**
     * 变量名称
     */
    String value() default "";

    boolean required() default true;
}
