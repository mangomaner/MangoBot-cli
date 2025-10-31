package org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.method;

import org.mango.mangobot.annotation.QQ.method.AudioMessage;
import org.mango.mangobot.annotation.QQ.method.ImageMessage;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.HandlerMatcher;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.AnnotationUtils;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.MessageTypeEnum;
import org.mango.mangobot.model.QQ.QQMessage;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;

@Component
public class AudioMessageMatcher implements HandlerMatcher {

    @Override
    public boolean supports(Annotation annotation) {
        return annotation instanceof ImageMessage;
    }
    @Override
    public Class<? extends Annotation> supportedAnnotationType() {
        return AudioMessage.class;
    }
    @Override
    public boolean matches(QQMessage message, Annotation annotation, boolean isSelfAt) {
        return AnnotationUtils.containsSegmentOfType(message, "audio");
    }

    @Override
    public MessageTypeEnum getSupportMessageType() {
        return MessageTypeEnum.AUDIO;
    }
}