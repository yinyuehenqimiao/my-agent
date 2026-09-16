package com.myagent.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.tool.ToolDefinition;
import com.myagent.framework.tool.ToolGateway;
import com.myagent.framework.tool.ToolInvocation;
import com.myagent.framework.tool.ToolResult;
import com.myagent.rag.core.mcp.McpToolExecutor;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

import java.util.List;
import java.util.Map;

/** 将 Tool Gateway 中的动态只读工具适配给 AgentScope，实际调用仍经过网关安全管道。 */
final class GatewayMcpToolExecutor implements McpToolExecutor {
    private final ToolGateway gateway;
    private final ToolDefinition definition;
    private final ExecutionIdentity identity;
    private final ObjectMapper json;
    private final Tool protocolDefinition;

    GatewayMcpToolExecutor(ToolGateway gateway, ToolDefinition definition,
                           ExecutionIdentity identity, ObjectMapper json) {
        this.gateway = gateway;
        this.definition = definition;
        this.identity = identity;
        this.json = json;
        Map<String, Object> schema = definition.getInputSchema() == null ? Map.of() : definition.getInputSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.getOrDefault("required", List.of());
        JsonSchema input = new JsonSchema((String) schema.getOrDefault("type", "object"),
                properties, required, false, null, null);
        this.protocolDefinition = Tool.builder().name(definition.getName())
                .description(definition.getDescription()).inputSchema(input)
                .annotations(new ToolAnnotations(null, definition.isReadOnly(), null, null, null, null))
                .build();
    }

    @Override public Tool getToolDefinition() { return protocolDefinition; }

    @Override
    public CallToolResult execute(Map<String, Object> parameters) {
        ToolResult result = gateway.invoke(ToolInvocation.builder()
                .toolName(definition.getName())
                .input(parameters == null ? Map.of() : parameters)
                .identity(identity)
                .source(ToolDefinition.Source.REACT)
                .build());
        try {
            String text = json.writeValueAsString(result.getOutput() == null
                    ? Map.of("status", result.getState().name(), "errorCode",
                    result.getErrorCode() == null ? "" : result.getErrorCode(), "message",
                    result.getMessage() == null ? "" : result.getMessage())
                    : Map.of("status", result.getState().name(), "data", result.getOutput()));
            return CallToolResult.builder().content(List.of(new TextContent(text)))
                    .isError(result.getState() != ToolResult.State.SUCCESS).build();
        } catch (Exception ex) {
            return CallToolResult.builder().content(List.of(new TextContent("工具结果序列化失败")))
                    .isError(true).build();
        }
    }
}
