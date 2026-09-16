package com.myagent.rag.core.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.framework.context.LoginUser;
import com.myagent.framework.tool.ToolDefinition;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpRuntimeToolCatalogTest {
    private JdbcTemplate jdbc;
    private McpRuntimeToolCatalog catalog;
    private LoginUser user;

    @BeforeEach
    void setUp() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:mcp-runtime-catalog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP ALL OBJECTS");
        new ResourceDatabasePopulator(
                new FileSystemResource("../resources/database/upgrades/v0.6.0/01_mcp_catalog.sql"),
                new FileSystemResource("../resources/database/upgrades/v0.6.0/02_mcp_intent_binding.sql"),
                new FileSystemResource("../resources/database/upgrades/v0.9.0/02_mcp_tool_contract.sql"),
                new FileSystemResource("../resources/database/upgrades/v0.10.0/01_runtime_tool_catalog.sql")
        ).execute(ds);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        Tool tool = Tool.builder().name("get_leave_balance").description("查询本人年假余额")
                .inputSchema(new JsonSchema("object", Map.of(), List.of(), false, null, null)).build();
        jdbc.update("INSERT INTO mcp_connector VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                "tenant-a", "erp", "人事 ERP", "http://erp/mcp", "vault:erp", "DISCOVERED", "admin");
        jdbc.update("""
                INSERT INTO mcp_catalog_tool(tenant_id,connector_id,tool_id,title,description,review_status,
                  enabled,discovered_at,contract_version,schema_hash,tool_schema,read_only)
                VALUES(?,?,?,?,?,'APPROVED',TRUE,CURRENT_TIMESTAMP,1,?,?,TRUE)
                """, "tenant-a", "erp", "get_leave_balance", "年假余额", "查询本人年假余额", "hash-1", json.writeValueAsString(tool));
        McpCatalogRevisionService revisions = mock(McpCatalogRevisionService.class);
        when(revisions.current("tenant-a")).thenReturn(7L);
        catalog = new McpRuntimeToolCatalog(jdbc, json, mock(McpRuntimeInvoker.class), revisions);
        user = LoginUser.builder().tenantId("tenant-a").userId("u1").build();
    }

    @Test
    void workflowSeesEnabledToolButReactRequiresPublishedIntentBinding() {
        assertThat(catalog.list(user, ToolDefinition.Source.WORKFLOW))
                .extracting(ToolDefinition::getName)
                .containsExactly("mcp:erp:get_leave_balance");
        assertThat(catalog.list(user, ToolDefinition.Source.REACT)).isEmpty();

        jdbc.update("INSERT INTO mcp_intent_binding VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                "tenant-a", "hr.leave.balance", "erp", "get_leave_balance", "PUBLISHED", "admin");

        assertThat(catalog.list(user, ToolDefinition.Source.REACT))
                .extracting(ToolDefinition::getName)
                .containsExactly("mcp:erp:get_leave_balance");
        assertThat(catalog.revision(user)).isEqualTo(7L);
    }

    @Test
    void disableTakesEffectOnNextResolve() {
        String name = "mcp:erp:get_leave_balance";
        assertThat(catalog.resolve(user, name, ToolDefinition.Source.WORKFLOW)).isPresent();
        jdbc.update("UPDATE mcp_catalog_tool SET enabled=FALSE");
        assertThat(catalog.resolve(user, name, ToolDefinition.Source.WORKFLOW)).isEmpty();
    }
}
