package com.myagent.erp.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** 空数据库启动时必须获得 ERP 读写所需的完整结构。 */
class ErpMigrationTest {

    @Test
    void migrationsCreateOrganizationLeaveAndAuditTables() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:erp-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);

        var jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='ERP_EMPLOYEE'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='ERP_LEAVE_REQUEST'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='ERP_OPERATION_AUDIT'", Integer.class)).isEqualTo(1);
    }
}
