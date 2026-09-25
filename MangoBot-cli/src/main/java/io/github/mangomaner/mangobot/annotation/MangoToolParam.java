package io.github.mangomaner.mangobot.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 描述 {@link MangoToolMethod} 方法的一个参数，用于生成 MCP tool 的 inputSchema。
 *
 * <p>不标注也能用：会退回反射拿到的真实参数名（编译带了 {@code -parameters}），
 * 只是模型看不到参数含义，调用准确率会掉。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface MangoToolParam {

    /**
     * 参数名——claude 就是按这个名字传实参的，必须和形参对得上。
     */
    String name();

    /**
     * 参数含义。
     */
    String description() default "";

    /**
     * 是否必填；false 的进不了 inputSchema 的 required 列表。
     */
    boolean required() default true;
}
