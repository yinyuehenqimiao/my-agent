package com.myagent.system.auth;

import com.myagent.framework.context.LoginUser;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.exception.ClientException;
import com.myagent.system.auth.service.PolicyDecisionService;
import com.myagent.system.auth.service.impl.PolicyDecisionServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 1 权限决策测试：RBAC/ABAC、默认拒绝、自审拦截、实时撤销、身份隔离。
 */
@SpringJUnitConfig(PolicyDecisionServiceTest.Config.class)
class PolicyDecisionServiceTest {

    @Configuration
    static class Config {
        @Bean(destroyMethod = "close") DataSource ds() {
            var ds = new com.zaxxer.hikari.HikariDataSource();
            ds.setJdbcUrl("jdbc:h2:mem:auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=YEAR,DAY");
            ds.setUsername("sa"); ds.setPassword(""); ds.setMaximumPoolSize(4); return ds;
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PolicyDecisionService policy(JdbcTemplate jdbc) { return new PolicyDecisionServiceImpl(jdbc); }
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PolicyDecisionService policy;

    private long adminUser = 100L;
    private long editorUser = 101L;
    private long auditorUser = 102L;
    private long multiRoleUser = 103L;
    private long noRoleUser = 104L;

    @BeforeEach void setup() {
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE sys_role(id BIGSERIAL PRIMARY KEY,role_code VARCHAR,role_name VARCHAR,description VARCHAR,enabled BOOLEAN,tenant_id VARCHAR,create_time TIMESTAMP,update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_permission(id BIGSERIAL PRIMARY KEY,perm_code VARCHAR,perm_name VARCHAR,description VARCHAR,category VARCHAR,enabled BOOLEAN,create_time TIMESTAMP,update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_user_role(id BIGSERIAL PRIMARY KEY,user_id BIGINT,role_code VARCHAR,tenant_id VARCHAR,create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_role_permission(id BIGSERIAL PRIMARY KEY,role_code VARCHAR,perm_code VARCHAR,create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_auth_audit(id BIGSERIAL PRIMARY KEY,user_id VARCHAR,username VARCHAR,tenant_id VARCHAR,perm_code VARCHAR,action VARCHAR,reason VARCHAR,trace_id VARCHAR,create_time TIMESTAMP)");
        // 角色
        jdbc.update("INSERT INTO sys_role(role_code,role_name,description,enabled,tenant_id,create_time,update_time) VALUES('PLATFORM_ADMIN','平台管理员',NULL,TRUE,'default',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO sys_role(role_code,role_name,description,enabled,tenant_id,create_time,update_time) VALUES('WORKFLOW_EDITOR','工作流编辑者',NULL,TRUE,'default',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO sys_role(role_code,role_name,description,enabled,tenant_id,create_time,update_time) VALUES('AUDITOR','审计员',NULL,TRUE,'default',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        // 权限码
        for (String[] p : new String[][]{
                {"employee:manage","员工管理","employee"},
                {"knowledge:authorize","知识授权","knowledge"},
                {"workflow:view","工作流查看","workflow"},
                {"workflow:edit","工作流编辑","workflow"},
                {"workflow:review","工作流审核","workflow"},
                {"audit:view","审计查看","audit"}}) {
            jdbc.update("INSERT INTO sys_permission(perm_code,perm_name,category,enabled,create_time,update_time) VALUES(?,?,?,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", p[0], p[1], p[2]);
        }
        // PLATFORM_ADMIN 拥有全部
        for (String p : List.of("employee:manage","knowledge:authorize","workflow:view","workflow:edit","workflow:review","audit:view")) {
            jdbc.update("INSERT INTO sys_role_permission(role_code,perm_code,create_time) VALUES('PLATFORM_ADMIN', ?, CURRENT_TIMESTAMP)", p);
        }
        // WORKFLOW_EDITOR
        for (String p : List.of("workflow:view","workflow:edit")) {
            jdbc.update("INSERT INTO sys_role_permission(role_code,perm_code,create_time) VALUES('WORKFLOW_EDITOR', ?, CURRENT_TIMESTAMP)", p);
        }
        // AUDITOR
        for (String p : List.of("audit:view","workflow:view")) {
            jdbc.update("INSERT INTO sys_role_permission(role_code,perm_code,create_time) VALUES('AUDITOR', ?, CURRENT_TIMESTAMP)", p);
        }
        // 用户分配
        jdbc.update("INSERT INTO sys_user_role(user_id,role_code,tenant_id,create_time) VALUES(?, 'PLATFORM_ADMIN', 'default', CURRENT_TIMESTAMP)", adminUser);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_code,tenant_id,create_time) VALUES(?, 'WORKFLOW_EDITOR', 'default', CURRENT_TIMESTAMP)", editorUser);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_code,tenant_id,create_time) VALUES(?, 'AUDITOR', 'default', CURRENT_TIMESTAMP)", auditorUser);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_code,tenant_id,create_time) VALUES(?, 'WORKFLOW_EDITOR', 'default', CURRENT_TIMESTAMP)", multiRoleUser);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_code,tenant_id,create_time) VALUES(?, 'AUDITOR', 'default', CURRENT_TIMESTAMP)", multiRoleUser);
        // noRoleUser 不分配任何角色
    }

    @AfterEach void cleanup() { UserContext.clear(); }

    private LoginUser identity(long userId, String username, String enterpriseRole) {
        return LoginUser.builder()
                .userId(String.valueOf(userId)).username(username).role("user")
                .tenantId("default").platformRoles(policy.rolesOf(userId))
                .enterpriseRole(enterpriseRole).build();
    }

    @Test void rolePermissionMapping() {
        assertEquals(List.of("PLATFORM_ADMIN"), policy.rolesOf(adminUser));
        assertTrue(policy.permissionsOf(adminUser).containsAll(
                List.of("employee:manage","knowledge:authorize","workflow:review","audit:view")));
        assertTrue(policy.permissionsOf(editorUser).containsAll(List.of("workflow:view","workflow:edit")));
        assertFalse(policy.permissionsOf(editorUser).contains("workflow:review"));
    }

    @Test void defaultDenyForNoRoleAndMissingIdentity() {
        assertFalse(policy.hasPermission(identity(noRoleUser, "nobody", "EMPLOYEE"), "workflow:view"));
        assertFalse(policy.hasPermission(null, "workflow:view"));
        assertFalse(policy.hasPermission(identity(noRoleUser, "nobody", "EMPLOYEE"), "nonexistent:perm"));
    }

    @Test void multiRoleUnionPermissions() {
        List<String> perms = policy.permissionsOf(multiRoleUser);
        assertTrue(perms.containsAll(List.of("workflow:view","workflow:edit","audit:view")));
        assertFalse(perms.contains("workflow:review"));
    }

    @Test void requirePermissionThrowsAndAuditsOnDeny() {
        LoginUser editor = identity(editorUser, "editor", "EMPLOYEE");
        assertDoesNotThrow(() -> policy.requirePermission(editor, "workflow:edit"));
        ClientException ex = assertThrows(ClientException.class, () -> policy.requirePermission(editor, "workflow:review"));
        assertTrue(ex.getMessage().contains("workflow:review"));
        Integer denies = jdbc.queryForObject("SELECT count(*) FROM sys_auth_audit WHERE action='DENY' AND perm_code='workflow:review'", Integer.class);
        assertEquals(1, denies, "权限拒绝应写入审计");
    }

    @Test void selfReviewBlocked() {
        LoginUser editor = identity(editorUser, "editor", "EMPLOYEE");
        // 编辑者审核自己提交 → 拦截
        assertThrows(ClientException.class, () -> policy.requireNotSelfReview(editor, editor.getUserId()));
        // 审核他人 → 通过
        assertDoesNotThrow(() -> policy.requireNotSelfReview(editor, "999"));
    }

    @Test void realTimeRevocationOnRoleRemoval() {
        assertTrue(policy.hasPermission(identity(editorUser, "editor", "EMPLOYEE"), "workflow:edit"));
        // 撤销角色后实时失效
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=? AND role_code='WORKFLOW_EDITOR'", editorUser);
        assertFalse(policy.hasPermission(identity(editorUser, "editor", "EMPLOYEE"), "workflow:edit"));
    }

    @Test void realTimeRevocationOnRoleDisable() {
        assertTrue(policy.hasPermission(identity(auditorUser, "auditor", "EMPLOYEE"), "audit:view"));
        jdbc.update("UPDATE sys_role SET enabled=FALSE WHERE role_code='AUDITOR'");
        assertFalse(policy.hasPermission(identity(auditorUser, "auditor", "EMPLOYEE"), "audit:view"));
    }

    @Test void platformAdminDoesNotAutoGetBusinessApprovalRole() {
        // 平台管理员的企业角色仍为 EMPLOYEE，不自动获得 MANAGER/HR 业务审批权
        LoginUser admin = identity(adminUser, "admin", "EMPLOYEE");
        assertEquals("EMPLOYEE", admin.getEnterpriseRole());
        assertNotEquals("MANAGER", admin.getEnterpriseRole());
        // 但拥有平台 employee:manage
        assertTrue(policy.hasPermission(admin, "employee:manage"));
    }

    @Test void asyncThreadIdentityIsolation() throws Exception {
        LoginUser main = identity(editorUser, "editor", "EMPLOYEE");
        UserContext.set(main);
        // 子线程设置自己的身份，不应污染父线程（隔离）
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> childSaw = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                UserContext.set(identity(adminUser, "admin", "EMPLOYEE"));
                childSaw.set(UserContext.getUserId());
            } finally {
                UserContext.clear();
                latch.countDown();
            }
        });
        t.start();
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        // 子线程看到自己设置的身份
        assertEquals(String.valueOf(adminUser), childSaw.get());
        // 父线程身份未被污染
        assertEquals(String.valueOf(editorUser), UserContext.getUserId());
    }

    @Test void missingIdentityFailsClosed() {
        UserContext.clear();
        assertThrows(ClientException.class, () -> policy.requirePermission(UserContext.requireIdentity(), "workflow:view"));
    }
}
