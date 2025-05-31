package org.mango.mangobot.manager.websocketReverseProxy.handler.impl.parameter;

import org.mango.mangobot.annotation.QQ.parameter.TargetId;
import org.mango.mangobot.manager.websocketReverseProxy.handler.ParameterArgumentResolver;
import org.mango.mangobot.model.QQ.QQMessage;
import org.mango.mangobot.model.QQ.ReceiveMessageSegment;
import org.mango.mangobot.utils.MethodParameter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TargetIdParameterArgumentResolver implements ParameterArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasAnnotation(TargetId.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, QQMessage message) {
        if(!(message.getTarget_id() == null)){  // 戳一戳对象
            return message.getTarget_id();
        } else {
            List<ReceiveMessageSegment> segments = message.getMessage();    // at对象
            StringBuilder sb = new StringBuilder();
            for (ReceiveMessageSegment segment : segments) {
                if ("at".equalsIgnoreCase(segment.getType())) {
                    sb.append(segment.getData().getQq());
                }
            }
            return sb.toString();
        }
    }
}