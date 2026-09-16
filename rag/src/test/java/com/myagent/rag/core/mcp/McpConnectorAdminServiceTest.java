package com.myagent.rag.core.mcp;

import com.myagent.framework.context.LoginUser;
import com.myagent.system.auth.service.PolicyDecisionService;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

/** 管理服务边界：发现得到待审核条目，只有审核通过且明确启用才成为可发布候选。 */
class McpConnectorAdminServiceTest {
    private JdbcTemplate jdbc;
    private McpConnectorAdminService service;
    private LoginUser admin;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:mcp-admin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        new ResourceDatabasePopulator(new FileSystemResource("../resources/database/upgrades/v0.6.0/01_mcp_catalog.sql")).execute(dataSource);
        new ResourceDatabasePopulator(new FileSystemResource("../resources/database/upgrades/v0.9.0/02_mcp_tool_contract.sql")).execute(dataSource);
        var remote = new McpRemoteToolDiscovery() {
            @Override public List<Tool> discover(McpConnectorDefinition connector, String token) {
                return List.of(Tool.builder().name("get_leave_balance").title("查询年假").description("查询本人年假余额")
                        .inputSchema(new JsonSchema("object", Map.of(), List.of(), false, null, null)).build());
            }
        };
        PolicyDecisionService policy = mock(PolicyDecisionService.class);
        doNothing().when(policy).requirePermission(any(), any());
        service = new McpConnectorAdminService(jdbc, policy, new McpCatalogDiscoveryService(remote), secretRef -> "short-lived-discovery-token");
        admin = LoginUser.builder().tenantId("tenant-a").userId("admin").username("admin").build();
    }

    @Test
    void discoveryCreatesPendingToolsAndReviewControlsEnablement() {
        service.save(admin, new McpConnectorAdminService.ConnectorInput("erp", "人事 ERP", "http://erp.example/mcp", "vault:erp-a"));

        assertThat(service.discover(admin, "erp")).singleElement().satisfies(tool -> {
            assertThat(tool.toolId()).isEqualTo("get_leave_balance");
            assertThat(tool.reviewStatus()).isEqualTo("PENDING_REVIEW");
            assertThat(tool.enabled()).isFalse();
        });
        service.review(admin, "erp", "get_leave_balance", new McpConnectorAdminService.ReviewInput("APPROVED", true));

        assertThat(service.tools(admin, "erp")).singleElement().satisfies(tool -> {
            assertThat(tool.reviewStatus()).isEqualTo("APPROVED");
            assertThat(tool.enabled()).isTrue();
        });
    }

    @Test
    void rediscoveryKeepsApprovalWhenContractIsUnchanged() {
        service.save(admin, new McpConnectorAdminService.ConnectorInput("erp", "人事 ERP", "http://erp.example/mcp", "vault:erp-a"));
        service.discover(admin, "erp");
        service.review(admin, "erp", "get_leave_balance", new McpConnectorAdminService.ReviewInput("APPROVED", true));

        service.discover(admin, "erp");

        assertThat(service.tools(admin, "erp")).singleElement().satisfies(tool -> {
            assertThat(tool.reviewStatus()).isEqualTo("APPROVED");
            assertThat(tool.enabled()).isTrue();
            assertThat(tool.contractVersion()).isEqualTo(1);
            assertThat(tool.schemaHash()).hasSize(64);
        });
    }
}
