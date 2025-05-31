package org.mango.mangobot.annotation.QQ.method;

import org.mango.mangobot.annotation.QQ.QQMessageHandlerType;

import java.lang.annotation.*;

/**
 * 文本消息事件
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@QQMessageHandlerType
public @interface TextMessage {}