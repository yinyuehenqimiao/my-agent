package com.myagent.erp.mcp;

import com.myagent.erp.identity.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@ConditionalOnProperty(name = "erp.mcp.jwt.public-key")
public class McpBearerIdentityFilter extends OncePerRequestFilter {
    static final String IDENTITY_ATTRIBUTE = McpBearerIdentityFilter.class.getName() + ".identity";
    private final ErpMcpJwtVerifier verifier;

    public McpBearerIdentityFilter(ErpMcpJwtVerifier verifier) { this.verifier = verifier; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith("/mcp"); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            request.setAttribute(IDENTITY_ATTRIBUTE, verifier.verifyBearer(request.getHeader("Authorization")));
            chain.doFilter(request, response);
        } catch (UnauthorizedException exception) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"ERP_MCP_UNAUTHORIZED\",\"message\":\"MCP 服务身份无效\"}");
        }
    }
}
