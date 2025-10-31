package org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.method;

import org.mango.mangobot.annotation.QQ.method.PokeMessage;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.HandlerMatcher;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.MessageTypeEnum;
import org.mango.mangobot.model.QQ.QQMessage;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;

@Component
public class PokeMessageMatcher implements HandlerMatcher {

    @Override
    public boolean supports(Annotation annotation) {
        return annotation instanceof PokeMessage;
    }
    @Override
    public Class<? extends Annotation> supportedAnnotationType() {
        return PokeMessage.class;
    }
    @Override
    public boolean matches(QQMessage message, Annotation annotation, boolean isSelfAt) {
        return "poke".equalsIgnoreCase(message.getSub_type())
                && (message.getMessage() == null || message.getMessage().isEmpty());
    }
    @Override
    public MessageTypeEnum getSupportMessageType() {
        return MessageTypeEnum.POKE;
    }
}