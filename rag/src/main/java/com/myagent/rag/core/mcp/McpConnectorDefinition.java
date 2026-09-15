package com.myagent.rag.core.mcp;

/** 已由后台审核的连接器定位信息；secretRef 只是密钥引用，绝不保存或返回真实凭证。 */
public record McpConnectorDefinition(String tenantId, String connectorId, String endpoint, String secretRef) {
    public McpConnectorDefinition {
        if (blank(tenantId) || blank(connectorId) || blank(endpoint) || blank(secretRef)) {
            throw new IllegalArgumentException("连接器租户、标识、地址和密钥引用不能为空");
        }
        if (!(endpoint.startsWith("https://") || endpoint.startsWith("http://"))) {
            throw new IllegalArgumentException("MCP 连接器地址必须使用 HTTP(S)");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
