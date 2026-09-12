package com.myagent.system.user.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.jwt.StpLogicJwtForStateless;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 登录拦截 + 用户上下文（对齐 ragent）
 */
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    public static final int ORDER_LOGIN = 0;
    public static final int ORDER_USER_CONTEXT = 20;

    private final UserContextInterceptor userContextInterceptor;

    /**
     * 将默认 Sa-Token 登录逻辑替换为无状态 JWT 实现。
     * JWT 的签发与验签均由 sa-token-jwt 提供，业务侧仍统一通过 StpUtil 和 UserContext 访问身份。
     */
    @PostConstruct
    public void registerJwtLoginLogic() {
        StpUtil.setStpLogic(new StpLogicJwtForStateless());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
                    ServletRequestAttributes attrs =
                            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (attrs != null) {
                        HttpServletRequest request = attrs.getRequest();
                        if (request.getDispatcherType() == DispatcherType.ASYNC) {
                            return;
                        }
                        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                            return;
                        }
                    }
                    StpUtil.checkLogin();
                }))
                .addPathPatterns("/api/**", "/knowledge-base/**", "/rag/**", "/ingestion/**", "/agent/**", "/intent-tree/**", "/agents/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/ping",
                        "/error",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**"
                )
                .order(ORDER_LOGIN);

        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/api/**", "/knowledge-base/**", "/rag/**", "/ingestion/**", "/agent/**", "/intent-tree/**", "/agents/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/ping",
                        "/error",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**"
                )
                .order(ORDER_USER_CONTEXT);
    }
}
