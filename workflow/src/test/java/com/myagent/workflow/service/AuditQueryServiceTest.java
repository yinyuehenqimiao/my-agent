package com.myagent.workflow.service;

import com.myagent.framework.context.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditQueryServiceTest {

    private JdbcTemplate jdbc;
    private AuditQueryService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:unified-audit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE operation_audit(id BIGINT PRIMARY KEY,operation VARCHAR,operator_id VARCHAR,"
                + "target_type VARCHAR,target_id VARCHAR,trace_id VARCHAR,tenant_id VARCHAR,detail VARCHAR,created_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_auth_audit(id BIGINT PRIMARY KEY,user_id VARCHAR,username VARCHAR,tenant_id VARCHAR,"
                + "perm_code VARCHAR,action VARCHAR,reason VARCHAR,trace_id VARCHAR,create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE ent_audit(id VARCHAR PRIMARY KEY,request_id VARCHAR,actor_id VARCHAR,action_id VARCHAR,"
                + "event VARCHAR,comment VARCHAR,tenant_id VARCHAR,trace_id VARCHAR,created_at TIMESTAMP)");
        jdbc.update("INSERT INTO operation_audit VALUES(1,'WF_PUBLISH','admin','wf_version','10','trace-wf','tenant-a','version=1',TIMESTAMP '2026-09-13 10:00:00')");
        jdbc.update("INSERT INTO operation_audit VALUES(2,'TOOL_EXECUTION','employee','tool','get_leave_balance','trace-tool','tenant-a','phase=COMPLETED',TIMESTAMP '2026-09-13 11:00:00')");
        jdbc.update("INSERT INTO sys_auth_audit VALUES(3,'attacker','攻击者','tenant-a','workflow:publish','DENY','缺少权限','trace-auth',TIMESTAMP '2026-09-13 12:00:00')");
        jdbc.update("INSERT INTO ent_audit VALUES('4','leave-1','manager',NULL,'APPROVED','主管通过','tenant-a','trace-biz',TIMESTAMP '2026-09-13 13:00:00')");
        jdbc.update("INSERT INTO operation_audit VALUES(5,'WF_PUBLISH','other','wf_version','99','trace-other','tenant-b','other',TIMESTAMP '2026-09-13 14:00:00')");
        service = new AuditQueryService(jdbc);
    }

    @Test
    void allCategoriesAreMergedNewestFirstAndStrictlyTenantScoped() {
        var rows = service.list(identity("tenant-a"), AuditQueryService.Category.ALL, 100);

        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(AuditQueryService.AuditEntry::category)
                .containsExactly(AuditQueryService.Category.BUSINESS, AuditQueryService.Category.AUTHORIZATION,
                        AuditQueryService.Category.TOOL, AuditQueryService.Category.PLATFORM);
        assertThat(rows).extracting(AuditQueryService.AuditEntry::actorId).doesNotContain("other");
    }

    @Test
    void categoryAndLimitAreAppliedAfterMerge() {
        assertThat(service.list(identity("tenant-a"), AuditQueryService.Category.TOOL, 100))
                .singleElement().extracting(AuditQueryService.AuditEntry::eventCode)
                .isEqualTo("TOOL_EXECUTION");
        assertThat(service.list(identity("tenant-a"), AuditQueryService.Category.ALL, 2)).hasSize(2);
    }

    @Test
    void missingTenantFailsClosedBeforeAnyQuery() {
        var identity = LoginUser.builder().userId("auditor").tenantId(" ").build();

        assertThatThrownBy(() -> service.list(identity, AuditQueryService.Category.ALL, 100))
                .hasMessageContaining("缺少租户身份");
    }

    private LoginUser identity(String tenantId) {
        return LoginUser.builder().userId("auditor").username("审计员").tenantId(tenantId).build();
    }
}
