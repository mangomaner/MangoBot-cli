package org.mango.mangobot.manager.websocketReverseProxy.dispatcher;

import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.HandlerMatcher;
import org.mango.mangobot.model.QQ.QQMessage;
import org.springframework.stereotype.Service;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HandlerMatcherDispatcher {

    // 将 注解 与 对应的注解处理器 做映射
    private final Map<Class<? extends Annotation>, HandlerMatcher> matcherMap;

    // 构造器注入所有HandlerMatcher对象
    public HandlerMatcherDispatcher(List<HandlerMatcher> matchers) {
        this.matcherMap = new HashMap<>();
        for (HandlerMatcher matcher : matchers) {
            Class<? extends Annotation> annotationType = matcher.supportedAnnotationType();
            matcherMap.put(annotationType, matcher);
        }
    }

    public HandlerMatcher getHandlerMatcher(Annotation annotation){
        return matcherMap.getOrDefault(annotation.annotationType(), null);
    }

    public boolean matches(Annotation annotation, QQMessage message, boolean isSelfAt) {
        Class<? extends Annotation> annotationType = annotation.annotationType();
        // 拿到当前注解的处理器，判断当前注解是否支持当前消息类型
        HandlerMatcher matcher = matcherMap.get(annotationType);

        if (matcher != null && matcher.supports(annotation)) {
            return matcher.matches(message, annotation, isSelfAt);
        }

        return false;
    }
}