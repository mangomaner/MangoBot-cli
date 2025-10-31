package org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl;

import org.mango.mangobot.model.QQ.QQMessage;

import java.lang.annotation.Annotation;

public interface HandlerMatcher {
    // 判断当前注解是否支持当前消息类型
    boolean supports(Annotation annotation);
    // 获取当前类对应的注解
    Class<? extends Annotation> supportedAnnotationType();
    // 判断当前注解是否匹配当前消息
    boolean matches(QQMessage message, Annotation annotation, boolean isSelfAt);
    // 获取当前注解所支持的消息类型
    MessageTypeEnum getSupportMessageType();
}