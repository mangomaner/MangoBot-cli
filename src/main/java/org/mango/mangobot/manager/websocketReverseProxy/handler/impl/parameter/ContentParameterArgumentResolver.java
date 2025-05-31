package org.mango.mangobot.manager.websocketReverseProxy.handler.impl.parameter;

import org.mango.mangobot.annotation.QQ.parameter.Content;
import org.mango.mangobot.manager.websocketReverseProxy.handler.ParameterArgumentResolver;
import org.mango.mangobot.model.QQ.QQMessage;
import org.mango.mangobot.model.QQ.ReceiveMessageSegment;
import org.mango.mangobot.utils.MethodParameter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContentParameterArgumentResolver implements ParameterArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasAnnotation(Content.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, QQMessage message) {
        List<ReceiveMessageSegment> segments = message.getMessage();
        StringBuilder sb = new StringBuilder();
        for (ReceiveMessageSegment segment : segments) {
            if ("text".equalsIgnoreCase(segment.getType())) {
                sb.append(segment.getData().getText());
            }
        }
        return sb.toString();
    }
}