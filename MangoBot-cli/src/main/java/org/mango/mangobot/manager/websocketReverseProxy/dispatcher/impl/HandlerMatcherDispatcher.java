package org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl;

import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.HandlerMatcher;
import org.mango.mangobot.model.QQ.QQMessage;
import org.springframework.stereotype.Service;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HandlerMatcherDispatcher {

    private final Map<Class<? extends Annotation>, HandlerMatcher> matcherMap;

    public HandlerMatcherDispatcher(List<HandlerMatcher> matchers) {
        this.matcherMap = new HashMap<>();
        for (HandlerMatcher matcher : matchers) {
            Class<? extends Annotation> annotationType = matcher.supportedAnnotationType();
            matcherMap.put(annotationType, matcher);
        }
    }

    public boolean matches(Annotation annotation, QQMessage message, boolean isSelfAt) {
        Class<? extends Annotation> annotationType = annotation.annotationType();
        HandlerMatcher matcher = matcherMap.get(annotationType);

        if (matcher != null && matcher.supports(annotation)) {
            return matcher.matches(message, annotation, isSelfAt);
        }

        return false;
    }
}