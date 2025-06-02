package org.mango.mangobot.QQ;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 元注解，用于标记哪些注解是 QQ 消息处理器使用的
 */
@Target(ElementType.ANNOTATION_TYPE) // 表示这个注解是用来标注“其他注解”的
@Retention(RetentionPolicy.RUNTIME)
public @interface QQMessageHandlerType {
}