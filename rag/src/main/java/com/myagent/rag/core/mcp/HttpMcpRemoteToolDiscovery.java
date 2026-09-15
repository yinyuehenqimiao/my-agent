package com.myagent.rag.core.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/** 仅用于管理端的 tools/list 发现；真实令牌由上层从密钥引用解析后短暂传入。 */
@Component
public class HttpMcpRemoteToolDiscovery implements McpRemoteToolDiscovery {
    @Override
    public List<Tool> discover(McpConnectorDefinition connector, String accessToken) {
        if (accessToken == null || accessToken.isBlank()) throw new IllegalArgumentException("MCP 发现令牌不能为空");
        String endpoint = connector.endpoint().endsWith("/mcp") ? connector.endpoint() : connector.endpoint() + "/mcp";
        var transport = HttpClientStreamableHttpTransport.builder(endpoint)
                .customizeRequest(request -> request.header("Authorization", "Bearer " + accessToken))
                .build();
        McpSyncClient client = McpClient.sync(transport).clientInfo(new Implementation("my-agent-catalog", "0.1.0")).build();
        try {
            client.initialize();
            return List.copyOf(client.listTools().tools());
        } finally {
            client.close();
        }
    }
}
