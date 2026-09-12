package com.myagent.system.user.config;

import cn.dev33.satoken.stp.StpUtil;
import com.myagent.framework.context.LoginUser;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.exception.ClientException;
import com.myagent.system.auth.service.ExecutionIdentityEnricher;
import com.myagent.system.auth.service.PolicyDecisionService;
import com.myagent.system.user.dao.entity.UserDO;
import com.myagent.system.user.dao.mapper.UserMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 从 Sa-Token 取 loginId，查库写入 UserContext，并装配统一执行身份：
 * 平台角色（RBAC）+ 企业业务属性（ABAC，经 ExecutionIdentityEnricher 富化）。
 */
@Component
@RequiredArgsConstructor
public class UserContextInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;
    private final PolicyDecisionService policyDecisionService;
    /** 企业属性富化 SPI，enterprise 模块未启用时为空 */
    private final ObjectProvider<ExecutionIdentityEnricher> enricherProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String loginId = StpUtil.getLoginIdAsString();
        UserDO user = userMapper.selectById(Long.valueOf(loginId));
        if (user == null) {
            throw new ClientException("用户不存在或已删除");
        }
        Long uid = user.getId();
        List<String> platformRoles = policyDecisionService.rolesOf(uid);

        LoginUser snapshot = LoginUser.builder()
                .userId(String.valueOf(uid))
                .username(user.getUsername())
                .role(user.getRole())
                .tenantId("default")
                .platformRoles(platformRoles == null ? List.of() : platformRoles)
                .build();

        // 企业业务属性富化（ABAC），enricher 可选
        ExecutionIdentityEnricher enricher = enricherProvider.getIfAvailable();
        if (enricher != null) {
            snapshot = enricher.enrich(snapshot);
        }

        UserContext.set(snapshot);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
