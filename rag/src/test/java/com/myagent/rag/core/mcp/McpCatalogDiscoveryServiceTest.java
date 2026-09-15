package com.myagent.rag.core.mcp;

import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 租户级 MCP 目录刷新是管理后台审核发布前的公共应用服务边界。 */
class McpCatalogDiscoveryServiceTest {

    @Test
    void keepsTheLastSuccessfulTenantSnapshotWhenRediscoveryFails() {
        var remote = new StubRemoteDiscovery(List.of(tool("get_leave_balance")));
        var catalog = new McpCatalogDiscoveryService(remote);
        var connector = new McpConnectorDefinition("tenant-a", "erp-main", "http://erp.example/mcp", "vault:erp-a");

        assertThat(catalog.refresh(connector, "discovery-token").tools())
                .extracting(McpCatalogTool::toolId).containsExactly("get_leave_balance");

        remote.fail = true;
        assertThatThrownBy(() -> catalog.refresh(connector, "discovery-token"))
                .hasMessageContaining("MCP 工具发现失败");
        assertThat(catalog.list("tenant-a", "erp-main").tools())
                .extracting(McpCatalogTool::toolId).containsExactly("get_leave_balance");
    }

    @Test
    void isolatesToolCatalogsBetweenTenants() {
        var remote = new StubRemoteDiscovery(List.of(tool("get_my_profile")));
        var catalog = new McpCatalogDiscoveryService(remote);

        catalog.refresh(new McpConnectorDefinition("tenant-a", "erp", "http://a/mcp", "vault:a"), "token-a");
        remote.tools = List.of(tool("get_leave_balance"));
        catalog.refresh(new McpConnectorDefinition("tenant-b", "erp", "http://b/mcp", "vault:b"), "token-b");

        assertThat(catalog.list("tenant-a", "erp").tools()).extracting(McpCatalogTool::toolId).containsExactly("get_my_profile");
        assertThat(catalog.list("tenant-b", "erp").tools()).extracting(McpCatalogTool::toolId).containsExactly("get_leave_balance");
    }

    private static Tool tool(String name) {
        return Tool.builder().name(name).description(name).inputSchema(new JsonSchema("object", Map.of(), List.of(), false, null, null)).build();
    }

    private static final class StubRemoteDiscovery implements McpRemoteToolDiscovery {
        private List<Tool> tools;
        private boolean fail;

        private StubRemoteDiscovery(List<Tool> tools) { this.tools = tools; }

        @Override
        public List<Tool> discover(McpConnectorDefinition connector, String accessToken) {
            if (fail) throw new IllegalStateException("remote unavailable");
            return tools;
        }
    }
}
