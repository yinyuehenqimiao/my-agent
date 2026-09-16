package com.myagent.erp.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/** Streamable HTTP MCP 入口；HTTP 身份由过滤器验证后传给各工具执行器。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "erp.mcp.jwt.public-key")
@EnableConfigurationProperties(ErpMcpJwtProperties.class)
public class ErpMcpServerConfiguration {
    @Bean
    HttpServletStreamableServerTransportProvider erpMcpTransport() {
        return HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint("/mcp")
                .contextExtractor(this::transportContext)
                .build();
    }

    @Bean
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> erpMcpServlet(
            HttpServletStreamableServerTransportProvider transport) {
        return new ServletRegistrationBean<>(transport, "/mcp", "/mcp/");
    }

    @Bean(destroyMethod = "close")
    McpSyncServer erpMcpServer(HttpServletStreamableServerTransportProvider transport, ErpMcpToolService tools) {
        return McpServer.sync(transport).serverInfo("internal-erp", "0.1.0").tools(tools.specifications()).build();
    }

    private McpTransportContext transportContext(HttpServletRequest request) {
        Object identity = request.getAttribute(McpBearerIdentityFilter.IDENTITY_ATTRIBUTE);
        return McpTransportContext.create(identity == null ? Map.of() : Map.of(McpBearerIdentityFilter.IDENTITY_ATTRIBUTE, identity));
    }
}
