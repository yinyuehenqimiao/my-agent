package com.myagent.enterprise.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.myagent.framework.context.LoginUser;
import com.myagent.framework.enterprise.EnterpriseSystemPort;
import com.myagent.framework.enterprise.LeaveRequestCommand;
import com.myagent.framework.enterprise.LeaveRequestResult;
import com.myagent.framework.exception.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** HTTP ERP 写入的公开确认边界：准备不写 ERP，确认按租户和用户幂等执行。 */
@SpringJUnitConfig(ExternalErpActionServiceTest.Config.class)
class ExternalErpActionServiceTest {
    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean(destroyMethod = "close") com.zaxxer.hikari.HikariDataSource dataSource() {
            var dataSource = new com.zaxxer.hikari.HikariDataSource();
            dataSource.setJdbcUrl("jdbc:h2:mem:external_erp_actions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            return dataSource;
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().registerModule(new JavaTimeModule()); }
        @Bean Clock enterpriseClock() { return Clock.fixed(Instant.parse("2026-09-10T02:00:00Z"), ZoneId.of("Asia/Shanghai")); }
        @Bean RecordingErp erp() { return new RecordingErp(); }
        @Bean ExternalErpActionService service(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock enterpriseClock, RecordingErp erp) {
            return new ExternalErpActionService(jdbcTemplate, objectMapper, enterpriseClock, erp);
        }
    }

    static class RecordingErp implements EnterpriseSystemPort {
        final AtomicInteger createCalls = new AtomicInteger();
        @Override public LeaveRequestResult createLeaveRequest(com.myagent.framework.context.ExecutionIdentity identity, LeaveRequestCommand command) {
            createCalls.incrementAndGet();
            return new LeaveRequestResult("erp-leave-1", "PENDING_MANAGER", command.getTraceId());
        }
        @Override public com.myagent.framework.enterprise.EmployeeProfile getCurrentEmployee(com.myagent.framework.context.ExecutionIdentity identity) { throw new UnsupportedOperationException(); }
        @Override public com.myagent.framework.enterprise.LeaveBalance getLeaveBalance(com.myagent.framework.context.ExecutionIdentity identity, int year) { throw new UnsupportedOperationException(); }
        @Override public com.myagent.framework.enterprise.EmployeeTenure getCurrentEmployeeTenure(com.myagent.framework.context.ExecutionIdentity identity) { throw new UnsupportedOperationException(); }
        @Override public com.myagent.framework.enterprise.EmployeeBusinessPermissions getCurrentEmployeePermissions(com.myagent.framework.context.ExecutionIdentity identity) { throw new UnsupportedOperationException(); }
        @Override public java.util.List<com.myagent.framework.enterprise.LeaveRequestSummary> getMyLeaveRequests(com.myagent.framework.context.ExecutionIdentity identity) { throw new UnsupportedOperationException(); }
    }

    @org.springframework.beans.factory.annotation.Autowired ExternalErpActionService service;
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired RecordingErp erp;

    @BeforeEach void setup() {
        jdbc.execute("DROP ALL OBJECTS");
        new ResourceDatabasePopulator(new org.springframework.core.io.FileSystemResource("../resources/database/upgrades/v0.5.0/01_erp_pending_action.sql"))
                .execute(jdbc.getDataSource());
    }

    @Test void preparationDoesNotWriteErpAndConfirmationIsIdempotent() {
        var pending = service.prepareCreateLeave(employee("tenant-a", "employee"), "conversation", "key-1", command());

        assertEquals("PENDING", pending.status());
        assertEquals(0, erp.createCalls.get());
        assertEquals("DONE", service.confirm(employee("tenant-a", "employee"), pending.id()).status());
        assertEquals("DONE", service.confirm(employee("tenant-a", "employee"), pending.id()).status());
        assertEquals(1, erp.createCalls.get());
    }

    @Test void anotherTenantOrUserCannotConfirmTheCard() {
        var pending = service.prepareCreateLeave(employee("tenant-a", "employee"), "conversation", "key-1", command());

        assertThrows(ClientException.class, () -> service.confirm(employee("tenant-b", "employee"), pending.id()));
        assertThrows(ClientException.class, () -> service.confirm(employee("tenant-a", "other"), pending.id()));
        assertEquals(0, erp.createCalls.get());
    }

    private LoginUser employee(String tenantId, String userId) {
        return LoginUser.builder().tenantId(tenantId).userId(userId).username(userId).build();
    }

    private LeaveRequestCommand command() {
        return LeaveRequestCommand.builder().leaveType("ANNUAL").startDate("2026-09-14").endDate("2026-09-15")
                .reason("家庭安排").idempotencyKey("key-1").traceId("trace-1").conversationId("conversation").build();
    }
}
