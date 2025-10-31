package org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.method;

import org.mango.mangobot.annotation.QQ.method.TextMessage;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.HandlerMatcher;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.AnnotationUtils;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.MessageTypeEnum;
import org.mango.mangobot.model.QQ.QQMessage;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;

@Component
public class TextMessageMatcher implements HandlerMatcher {

    @Override
    public boolean supports(Annotation annotation) {
        return annotation instanceof TextMessage;
    }
    @Override
    public Class<? extends Annotation> supportedAnnotationType() {
        return TextMessage.class;
    }
    @Override
    public boolean matches(QQMessage message, Annotation annotation, boolean isSelfAt) {
        return AnnotationUtils.containsSegmentOfType(message, "text");
    }
    @Override
    public MessageTypeEnum getSupportMessageType() {
        return MessageTypeEnum.TEXT;
    }
}