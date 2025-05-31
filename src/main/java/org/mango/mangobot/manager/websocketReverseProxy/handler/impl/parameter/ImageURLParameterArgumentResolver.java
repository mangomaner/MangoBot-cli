package org.mango.mangobot.manager.websocketReverseProxy.handler.impl.parameter;

import org.mango.mangobot.annotation.QQ.parameter.ImageURL;
import org.mango.mangobot.manager.websocketReverseProxy.handler.ParameterArgumentResolver;
import org.mango.mangobot.model.QQ.QQMessage;
import org.mango.mangobot.model.QQ.ReceiveMessageSegment;
import org.mango.mangobot.utils.MethodParameter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ImageURLParameterArgumentResolver implements ParameterArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasAnnotation(ImageURL.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, QQMessage message) {
        List<ReceiveMessageSegment> segments = message.getMessage();
        StringBuilder sb = new StringBuilder();
        for (ReceiveMessageSegment segment : segments) {
            if ("image".equalsIgnoreCase(segment.getType())) {
                sb.append(segment.getData().getUrl());
                sb.append("\n");
            }
        }
        if(!sb.isEmpty())
            sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }
}