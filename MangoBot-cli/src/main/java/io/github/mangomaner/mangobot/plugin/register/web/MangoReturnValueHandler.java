package io.github.mangomaner.mangobot.plugin.register.web;

import io.github.mangomaner.mangobot.annotation.web.MangoBotRequestMapping;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor;

import java.util.List;

/**
 * 处理 @MangoBotController 标记的控制器返回值。
 * 将返回值作为 Response Body 处理，类似于 @RestController。
 */
public class MangoReturnValueHandler implements HandlerMethodReturnValueHandler {

    private final RequestResponseBodyMethodProcessor delegate;

    public MangoReturnValueHandler(List<HttpMessageConverter<?>> converters, ContentNegotiationManager manager) {
        // 复用 Spring 原生的 RequestResponseBodyMethodProcessor 逻辑
        this.delegate = new RequestResponseBodyMethodProcessor(converters, manager);
    }

    public MangoReturnValueHandler(List<HttpMessageConverter<?>> converters) {
        this.delegate = new RequestResponseBodyMethodProcessor(converters);
    }

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        // 只要类上标记了 @MangoBotRequestMapping，就由本 Handler 处理
        return returnType.getContainingClass().isAnnotationPresent(MangoBotRequestMapping.class);
    }

    @Override
    public void handleReturnValue(Object returnValue, MethodParameter returnType, 
                                  ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
        // 委托给 Spring 原生处理器完成内容协商和消息转换
        delegate.handleReturnValue(returnValue, returnType, mavContainer, webRequest);
    }
}
