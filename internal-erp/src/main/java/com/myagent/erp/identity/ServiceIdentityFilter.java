package com.myagent.erp.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ServiceIdentityFilter extends OncePerRequestFilter {
    private final byte[] expectedToken;

    public ServiceIdentityFilter(@Value("${erp.security.service-token:}") String expectedToken) {
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health") || request.getRequestURI().startsWith("/mcp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader("X-ERP-Service-Token");
        String tenantId = request.getHeader("X-Tenant-Id");
        String employeeId = request.getHeader("X-Employee-Id");
        String traceId = request.getHeader("X-Trace-Id");
        if (expectedToken.length == 0 || token == null
                || !MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8))
                || blank(tenantId) || blank(employeeId) || blank(traceId)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"ERP_UNAUTHORIZED\",\"message\":\"服务凭证或调用身份无效\",\"data\":null}");
            return;
        }
        try {
            ErpIdentityContext.set(new ErpIdentity(tenantId, employeeId, traceId));
            chain.doFilter(request, response);
        } finally {
            ErpIdentityContext.clear();
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
