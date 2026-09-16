package com.myagent.rag.core.mcp;

import org.springframework.stereotype.Component;

/** 本地/容器实现；生产可替换为 Vault 解析器。 */
@Component
public class EnvironmentMcpDiscoveryTokenProvider implements McpDiscoveryTokenProvider {
    @Override
    public String resolve(String secretRef) {
        String key = "MCP_DISCOVERY_TOKEN_" + secretRef.toUpperCase().replaceAll("[^A-Z0-9]", "_");
        String token = System.getenv(key);
        if (token == null || token.isBlank()) throw new IllegalStateException("连接器密钥引用未解析到发现令牌");
        return token;
    }
}
