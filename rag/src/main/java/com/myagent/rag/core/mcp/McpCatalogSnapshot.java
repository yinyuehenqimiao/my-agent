package com.myagent.rag.core.mcp;

import java.time.Instant;
import java.util.List;

/** 一次完整且成功的远程 MCP tools/list 结果。 */
public record McpCatalogSnapshot(String tenantId, String connectorId, Instant discoveredAt, List<McpCatalogTool> tools) {
    public McpCatalogSnapshot { tools = List.copyOf(tools); }
}
