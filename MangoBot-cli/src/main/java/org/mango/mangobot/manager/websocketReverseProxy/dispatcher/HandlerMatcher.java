package org.mango.mangobot.manager.websocketReverseProxy.dispatcher;

import org.mango.mangobot.model.QQ.QQMessage;

import java.lang.annotation.Annotation;

public interface HandlerMatcher {
    boolean supports(Annotation annotation);
    Class<? extends Annotation> supportedAnnotationType();
    boolean matches(QQMessage message, Annotation annotation, boolean isSelfAt);
}