package com.myagent.rag.core.mcp;

import com.myagent.framework.tool.ToolInvocation;
import com.myagent.framework.tool.ToolResult;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 使用服务端凭证调用动态发现的 MCP 工具；客户端不接收模型提供的地址或令牌。 */
@Component
public class McpRuntimeInvoker {
    private final McpDiscoveryTokenProvider tokenProvider;

    public McpRuntimeInvoker(McpDiscoveryTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    public ToolResult invoke(RuntimeConnection connection, ToolInvocation invocation) {
        String token = tokenProvider.resolve(connection.secretRef());
        String endpoint = connection.endpoint().endsWith("/mcp")
                ? connection.endpoint() : connection.endpoint() + "/mcp";
        var transport = HttpClientStreamableHttpTransport.builder(endpoint)
                .customizeRequest(request -> request.header("Authorization", "Bearer " + token))
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new Implementation("my-agent-runtime", "1.0.0"))
                .build();
        try {
            client.initialize();
            CallToolResult result = client.callTool(new CallToolRequest(
                    connection.remoteToolId(), invocation.getInput() == null ? Map.of() : invocation.getInput()));
            if (result != null && Boolean.TRUE.equals(result.isError())) {
                return ToolResult.error("REMOTE_TOOL_ERROR", text(result), invocation.getTraceId());
            }
            return ToolResult.success(Map.of("content", text(result)), invocation.getTraceId());
        } finally {
            client.close();
        }
    }

    private String text(CallToolResult result) {
        if (result == null || result.content() == null) return "";
        List<String> values = result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .map(TextContent::text)
                .toList();
        return String.join("\n", values);
    }

    public record RuntimeConnection(String endpoint, String secretRef, String remoteToolId) { }
}
