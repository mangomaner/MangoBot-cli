package io.github.mangomaner.mangobot.annotation.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标识一个类为 MangoBot 插件控制器，用于接收 Web 请求。
 * 类似于 Spring 的 @Controller / @RestController。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MangoBotController {
    /**
     * Bean 名称，可选
     */
    String value() default "";
}
