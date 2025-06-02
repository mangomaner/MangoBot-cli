package org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.method;

import org.mango.mangobot.annotation.QQ.method.AtMessage;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.HandlerMatcher;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.AnnotationUtils;
import org.mango.mangobot.model.QQ.QQMessage;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;

@Component
public class AtMessageMatcher implements HandlerMatcher {

    @Override
    public boolean supports(Annotation annotation) {
        return annotation instanceof AtMessage;
    }
    @Override
    public Class<? extends Annotation> supportedAnnotationType() {
        return AtMessage.class;
    }
    @Override
    public boolean matches(QQMessage message, Annotation annotation, boolean isSelfAt) {
        AtMessage anno = (AtMessage) annotation;
        return anno.self() == isSelfAt && AnnotationUtils.containsSegmentOfType(message, "at");
    }
}