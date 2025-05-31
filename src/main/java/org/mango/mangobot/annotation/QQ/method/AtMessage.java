package org.mango.mangobot.annotation.QQ.method;

import org.mango.mangobot.annotation.QQ.QQMessageHandlerType;

import java.lang.annotation.*;

/**
 * at消息事件
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@QQMessageHandlerType
public @interface AtMessage {
    boolean self() default true; // 是否只响应 @bot
}