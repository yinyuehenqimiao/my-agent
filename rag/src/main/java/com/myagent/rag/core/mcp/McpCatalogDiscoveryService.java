package com.myagent.rag.core.mcp;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 租户连接器的发现缓存。失败时不污染最近一次成功快照；发布审批由上层后台完成。
 */
@Service
public class McpCatalogDiscoveryService {
    private final McpRemoteToolDiscovery remote;
    private final ConcurrentMap<String, McpCatalogSnapshot> snapshots = new ConcurrentHashMap<>();

    public McpCatalogDiscoveryService(McpRemoteToolDiscovery remote) { this.remote = remote; }

    public McpCatalogSnapshot refresh(McpConnectorDefinition connector, String discoveryAccessToken) {
        try {
            List<McpCatalogTool> tools = remote.discover(connector, discoveryAccessToken).stream()
                    .filter(tool -> tool != null && tool.name() != null && !tool.name().isBlank())
                    .map(McpCatalogTool::from)
                    .sorted(Comparator.comparing(McpCatalogTool::toolId))
                    .toList();
            McpCatalogSnapshot snapshot = new McpCatalogSnapshot(connector.tenantId(), connector.connectorId(), Instant.now(), tools);
            snapshots.put(key(connector.tenantId(), connector.connectorId()), snapshot);
            return snapshot;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("MCP 工具发现失败，已保留最近一次成功目录", exception);
        }
    }

    public McpCatalogSnapshot list(String tenantId, String connectorId) {
        McpCatalogSnapshot snapshot = snapshots.get(key(tenantId, connectorId));
        if (snapshot == null) throw new IllegalArgumentException("该租户连接器尚未完成成功发现");
        return snapshot;
    }

    private static String key(String tenantId, String connectorId) { return tenantId + "\u0000" + connectorId; }
}
