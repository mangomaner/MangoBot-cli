package org.mango.mangobot.annotation.QQ.method;

import org.mango.mangobot.annotation.QQ.QQMessageHandlerType;

import java.lang.annotation.*;

/**
 * At、回复、文字、图片消息，当消息中 **只** 包含 At、回复、文字、图片 的一种或几种消息时，会触发该方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@QQMessageHandlerType
public @interface DefaultMessage {
}
