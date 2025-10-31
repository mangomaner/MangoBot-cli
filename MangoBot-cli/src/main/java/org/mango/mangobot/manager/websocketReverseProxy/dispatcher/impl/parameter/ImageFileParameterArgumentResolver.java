package org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.parameter;

import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.ParameterArgumentResolver;
import org.mango.mangobot.model.QQ.QQMessage;
import org.mango.mangobot.model.QQ.ReceiveMessageSegment;
import org.mango.mangobot.utils.MethodParameter;

import java.util.List;

public class ImageFileParameterArgumentResolver  implements ParameterArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return false;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, QQMessage message) {
        List<ReceiveMessageSegment> segments = message.getMessage();
        StringBuilder sb = new StringBuilder();
        if(segments == null) return "";
        for (ReceiveMessageSegment segment : segments) {
            if ("image".equalsIgnoreCase(segment.getType())) {
                sb.append(segment.getData().getFile());
                sb.append("\n");
            }
        }
        if(!sb.isEmpty())
            sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }
}
