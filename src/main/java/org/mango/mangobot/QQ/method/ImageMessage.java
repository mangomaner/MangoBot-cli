package org.mango.mangobot.QQ.method;

import org.mango.mangobot.QQ.QQMessageHandlerType;

import java.lang.annotation.*;

/**
 * 图片消息事件
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@QQMessageHandlerType
public @interface ImageMessage {}