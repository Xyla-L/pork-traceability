package com.pork.agent.config;

import com.pork.agent.support.AgentContext;
import com.pork.core.source.OperatorHeader;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 把 {@code X-User-*} 头装配成 {@link AgentContext} 注入控制器参数。
 * <p>
 * 只读请求头、不做兜底：头里没有就是没有，宁可后续报「未登录」，
 * 也不要给一个默认账号——那会让「匿名」看起来像某个真实的人。
 */
@Component
public class AgentContextResolver implements HandlerMethodArgumentResolver, WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(java.util.List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AgentContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                  NativeWebRequest request, WebDataBinderFactory binderFactory) {
        return new AgentContext(
                request.getHeader("X-User-Id"),
                OperatorHeader.decode(request.getHeader("X-User-Name")),
                request.getHeader("X-User-Role"),
                request.getHeader("X-User-Org"));
    }
}
