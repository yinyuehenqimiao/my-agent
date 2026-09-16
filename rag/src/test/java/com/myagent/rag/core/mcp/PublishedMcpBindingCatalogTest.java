package com.myagent.rag.core.mcp;

import com.myagent.framework.context.LoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

class PublishedMcpBindingCatalogTest {
    @Test void exposesOnlyPublishedReviewedEnabledBindingsForTheCurrentTenant() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:mcp-runtime;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        new ResourceDatabasePopulator(new FileSystemResource("../resources/database/upgrades/v0.6.0/01_mcp_catalog.sql"),
                new FileSystemResource("../resources/database/upgrades/v0.6.0/02_mcp_intent_binding.sql")).execute(ds);
        jdbc.update("INSERT INTO mcp_connector VALUES('tenant-a','erp','ERP','http://erp/mcp','vault:erp','DISCOVERED','admin',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO mcp_catalog_tool VALUES('tenant-a','erp','balance','余额','余额','APPROVED',TRUE,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO mcp_intent_binding VALUES('tenant-a','hr.balance','erp','balance','PUBLISHED','admin',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO mcp_intent_binding VALUES('tenant-a','hr.draft','erp','balance','DRAFT','admin',CURRENT_TIMESTAMP)");

        var identity = LoginUser.builder().tenantId("tenant-a").build();
        assertThat(new PublishedMcpBindingCatalog(jdbc).list(identity)).extracting(PublishedMcpBindingCatalog.PublishedBinding::intentCode)
                .containsExactly("hr.balance");
    }
}
