package io.github.mangomaner.mangobot.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PluginDescribe {
    String name();
    String author();
    String version();
    String description();
//    boolean enable() default false;
    boolean enableWeb() default false;
}
