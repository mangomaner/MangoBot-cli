package org.mango.mangobot.annotation.QQ;

import java.lang.annotation.*;

/**
 * 元注解，用于标记哪些注解是 QQ 消息处理器使用的
 */
@Target(ElementType.ANNOTATION_TYPE) // 表示这个注解是用来标注“其他注解”的
@Retention(RetentionPolicy.RUNTIME)
public @interface QQMessageHandlerType {
}