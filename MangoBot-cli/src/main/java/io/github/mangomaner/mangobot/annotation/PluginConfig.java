package io.github.mangomaner.mangobot.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.Repeatable;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(PluginConfigs.class)
public @interface PluginConfig {
    String key();
    String value() default "";
    String description() default "";
    String explain() default "";
    String type() default "STRING";
    boolean editable() default true;
    String category() default "general";
}
