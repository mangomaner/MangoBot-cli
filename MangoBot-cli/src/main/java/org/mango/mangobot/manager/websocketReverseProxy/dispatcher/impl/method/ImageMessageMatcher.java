package org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.method;

import org.mango.mangobot.annotation.QQ.method.ImageMessage;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.HandlerMatcher;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.AnnotationUtils;
import org.mango.mangobot.model.QQ.QQMessage;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;

@Component
public class ImageMessageMatcher implements HandlerMatcher {

    @Override
    public boolean supports(Annotation annotation) {
        return annotation instanceof ImageMessage;
    }
    @Override
    public Class<? extends Annotation> supportedAnnotationType() {
        return ImageMessage.class;
    }
    @Override
    public boolean matches(QQMessage message, Annotation annotation, boolean isSelfAt) {
        return AnnotationUtils.containsSegmentOfType(message, "image");
    }
}