package com.myagent.rag.core.mcp;

import io.modelcontextprotocol.spec.McpSchema.Tool;

/** 工具快照只保留协议元数据，实际调用仍需经过平台网关。 */
public record McpCatalogTool(String toolId, String title, String description, Tool definition) {
    static McpCatalogTool from(Tool tool) {
        return new McpCatalogTool(tool.name(), tool.title(), tool.description(), tool);
    }
}
