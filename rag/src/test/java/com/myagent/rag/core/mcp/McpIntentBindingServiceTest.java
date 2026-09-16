package com.myagent.rag.core.mcp;

import com.myagent.framework.context.LoginUser;
import com.myagent.framework.exception.ClientException;
import com.myagent.system.auth.service.PolicyDecisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

class McpIntentBindingServiceTest {
    private JdbcTemplate jdbc;
    private McpIntentBindingService service;
    private LoginUser admin;

    @BeforeEach void setUp() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:mcp-binding;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds); jdbc.execute("DROP ALL OBJECTS");
        new ResourceDatabasePopulator(new FileSystemResource("../resources/database/upgrades/v0.6.0/01_mcp_catalog.sql"),
                new FileSystemResource("../resources/database/upgrades/v0.6.0/02_mcp_intent_binding.sql")).execute(ds);
        jdbc.update("INSERT INTO mcp_connector VALUES('tenant-a','erp','ERP','http://erp/mcp','vault:erp','DISCOVERED','admin',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO mcp_catalog_tool VALUES('tenant-a','erp','get_leave_balance','年假','余额','PENDING_REVIEW',FALSE,CURRENT_TIMESTAMP)");
        PolicyDecisionService policy = mock(PolicyDecisionService.class); doNothing().when(policy).requirePermission(any(), any());
        service = new McpIntentBindingService(jdbc, policy);
        admin = LoginUser.builder().tenantId("tenant-a").userId("admin").build();
    }

    @Test void bindsOnlyReviewedEnabledToolsAndRequiresExplicitPublish() {
        var input = new McpIntentBindingService.BindingInput("hr.leave.balance", "erp", "get_leave_balance");
        assertThatThrownBy(() -> service.bind(admin, input)).isInstanceOf(ClientException.class);
        jdbc.update("UPDATE mcp_catalog_tool SET review_status='APPROVED',enabled=TRUE");

        service.bind(admin, input);
        assertThat(service.list(admin)).singleElement().extracting(McpIntentBindingService.BindingView::status).isEqualTo("DRAFT");
        service.publish(admin, "hr.leave.balance");
        assertThat(service.list(admin)).singleElement().extracting(McpIntentBindingService.BindingView::status).isEqualTo("PUBLISHED");
    }
}
