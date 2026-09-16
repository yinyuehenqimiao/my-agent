package com.myagent.rag.core.mcp;

/** 租户 MCP 目录发生变化；事件内容不包含连接器凭证。 */
public record McpCatalogChangedEvent(String tenantId, long revision) { }
