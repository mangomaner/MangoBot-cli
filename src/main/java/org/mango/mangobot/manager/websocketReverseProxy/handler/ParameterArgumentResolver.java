package org.mango.mangobot.manager.websocketReverseProxy.handler;

import org.mango.mangobot.model.QQ.QQMessage;
import org.mango.mangobot.utils.MethodParameter;

/**
 * 管理参数的注解，根据参数类型，解析参数
 */
public interface ParameterArgumentResolver {
    public boolean supportsParameter(MethodParameter parameter);
    Object resolveArgument(MethodParameter parameter, QQMessage message);
}