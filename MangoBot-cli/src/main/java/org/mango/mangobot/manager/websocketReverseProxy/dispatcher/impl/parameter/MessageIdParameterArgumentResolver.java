package org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.parameter;

import org.mango.mangobot.annotation.QQ.parameter.MessageId;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.ParameterArgumentResolver;
import org.mango.mangobot.model.QQ.QQMessage;
import org.mango.mangobot.utils.MethodParameter;
import org.springframework.stereotype.Component;

@Component
public class MessageIdParameterArgumentResolver implements ParameterArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasAnnotation(MessageId.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, QQMessage message) {
        return message.getMessage_id();
    }
}