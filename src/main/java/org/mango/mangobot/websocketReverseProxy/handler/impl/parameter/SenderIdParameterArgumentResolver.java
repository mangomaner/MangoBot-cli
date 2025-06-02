package org.mango.mangobot.websocketReverseProxy.handler.impl.parameter;

import org.mango.mangobot.QQ.parameter.SenderId;
import org.mango.mangobot.model.QQ.QQMessage;
import org.mango.mangobot.utils.MethodParameter;
import org.mango.mangobot.websocketReverseProxy.handler.ParameterArgumentResolver;
import org.springframework.stereotype.Component;

@Component
public class SenderIdParameterArgumentResolver implements ParameterArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasAnnotation(SenderId.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, QQMessage message) {
        return message.getUser_id(); // 假设 user_id 表示发送者QQ号
    }
}