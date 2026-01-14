package io.github.mangomaner.mangobot.plugin.register.web;

import io.github.mangomaner.mangobot.annotation.MangoBotPathVariable;
import io.github.mangomaner.mangobot.annotation.MangoBotRequestBody;
import io.github.mangomaner.mangobot.annotation.MangoBotRequestParam;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.annotation.AbstractNamedValueMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor;

import java.util.List;
import java.util.Map;

/**
 * 处理 MangoBot 自定义参数注解的解析器集合
 */
public class MangoArgumentResolvers {

    /**
     * 处理 @MangoBotRequestBody
     */
    public static class RequestBodyResolver extends RequestResponseBodyMethodProcessor {

        public RequestBodyResolver(List<HttpMessageConverter<?>> converters) {
            super(converters);
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(MangoBotRequestBody.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, 
                                      NativeWebRequest webRequest, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) throws Exception {
            return readWithMessageConverters(webRequest, parameter, parameter.getGenericParameterType());
        }
    }

    /**
     * 处理 @MangoBotRequestParam
     */
    public static class RequestParamResolver extends AbstractNamedValueMethodArgumentResolver {

        public RequestParamResolver(ConfigurableBeanFactory beanFactory) {
            super(beanFactory);
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(MangoBotRequestParam.class);
        }

        @Override
        protected NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
            MangoBotRequestParam ann = parameter.getParameterAnnotation(MangoBotRequestParam.class);
            return new NamedValueInfo(ann.value(), ann.required(), 
                    ValueConstants.DEFAULT_NONE.equals(ann.defaultValue()) ? null : ann.defaultValue());
        }

        @Override
        protected Object resolveName(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
            String[] values = request.getParameterValues(name);
            if (values == null) return null;
            if (values.length == 1) return values[0];
            return values;
        }

        @Override
        protected void handleMissingValue(String name, MethodParameter parameter) throws MissingServletRequestParameterException {
            throw new MissingServletRequestParameterException(name, parameter.getNestedParameterType().getSimpleName());
        }
    }

    /**
     * 处理 @MangoBotPathVariable
     */
    public static class PathVariableResolver extends AbstractNamedValueMethodArgumentResolver {

        public PathVariableResolver(ConfigurableBeanFactory beanFactory) {
            super(beanFactory);
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(MangoBotPathVariable.class);
        }

        @Override
        protected NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
            MangoBotPathVariable ann = parameter.getParameterAnnotation(MangoBotPathVariable.class);
            return new NamedValueInfo(ann.value(), ann.required(), ValueConstants.DEFAULT_NONE);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Object resolveName(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
            Map<String, String> uriTemplateVariables = (Map<String, String>) request.getAttribute(
                    HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
            return (uriTemplateVariables != null) ? uriTemplateVariables.get(name) : null;
        }

        @Override
        protected void handleMissingValue(String name, MethodParameter parameter) throws MissingServletRequestParameterException {
            // Spring 的 PathVariable 通常不会 missing，因为 URL 匹配上了就有。
            // 除非是 required=true 但真的没获取到
            throw new MissingServletRequestParameterException(name, parameter.getNestedParameterType().getSimpleName());
        }
    }
}
