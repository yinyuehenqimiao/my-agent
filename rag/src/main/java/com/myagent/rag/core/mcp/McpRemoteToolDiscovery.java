package com.myagent.rag.core.mcp;

import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.util.List;

/** 外部协议边界；以假实现测试目录规则，避免把网络细节耦合进目录管理。 */
public interface McpRemoteToolDiscovery {
    List<Tool> discover(McpConnectorDefinition connector, String accessToken);
}
