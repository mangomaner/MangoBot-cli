package org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.method;

import org.mango.mangobot.annotation.QQ.method.DefaultMessage;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.AnnotationUtils;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.HandlerMatcher;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.MessageTypeEnum;
import org.mango.mangobot.model.QQ.QQMessage;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;

@Component
public class DefaultMessageMatcher implements HandlerMatcher {
    @Override
    public boolean supports(Annotation annotation) {
        return annotation instanceof DefaultMessage;
    }

    @Override
    public Class<? extends Annotation> supportedAnnotationType() {
        return DefaultMessage.class;
    }

    @Override
    public boolean matches(QQMessage message, Annotation annotation, boolean isSelfAt) {
        boolean jud = true;
        if(!AnnotationUtils.containsSegmentOfType(message, MessageTypeEnum.TEXT.getValue())) jud = false;
        if(!AnnotationUtils.containsSegmentOfType(message, MessageTypeEnum.AT.getValue())) jud = false;
        if(!AnnotationUtils.containsSegmentOfType(message, MessageTypeEnum.IMAGE.getValue())) jud = false;
        return jud;
    }

    @Override
    public MessageTypeEnum getSupportMessageType() {
        return MessageTypeEnum.DEFAULT;
    }
}
