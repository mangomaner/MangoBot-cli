package org.mango.mangobot.websocketReverseProxy.handler.impl.parameter;

import org.mango.mangobot.QQ.parameter.GroupId;
import org.mango.mangobot.model.QQ.QQMessage;
import org.mango.mangobot.utils.MethodParameter;
import org.mango.mangobot.websocketReverseProxy.handler.ParameterArgumentResolver;
import org.springframework.stereotype.Component;

@Component
public class GroupIdParameterArgumentResolver implements ParameterArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasAnnotation(GroupId.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, QQMessage message) {
        return message.getGroup_id(); // 假设 user_id 表示发送者QQ号
    }
}