package com.myagent.erp.leave;

import com.myagent.erp.identity.ErpIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeaveServiceTest {
    private LeaveService service;

    @BeforeEach
    void setUp() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:erp-leave;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE erp_leave_balance(tenant_id VARCHAR,employee_id VARCHAR,leave_year INT,total_days DECIMAL(6,2),used_days DECIMAL(6,2),frozen_days DECIMAL(6,2),version BIGINT,PRIMARY KEY(tenant_id,employee_id,leave_year))");
        jdbc.execute("CREATE TABLE erp_leave_request(id VARCHAR PRIMARY KEY,tenant_id VARCHAR,employee_id VARCHAR,start_date DATE,end_date DATE,leave_days DECIMAL(6,2),reason VARCHAR,status VARCHAR,manager_id VARCHAR,created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE erp_idempotency(caller_id VARCHAR,endpoint VARCHAR,idempotency_key VARCHAR,request_hash VARCHAR,resource_id VARCHAR,PRIMARY KEY(caller_id,endpoint,idempotency_key))");
        jdbc.execute("CREATE TABLE erp_employee(tenant_id VARCHAR,employee_id VARCHAR,business_role VARCHAR,manager_id VARCHAR)");
        jdbc.execute("CREATE TABLE erp_operation_audit(id VARCHAR,tenant_id VARCHAR,actor_id VARCHAR,event VARCHAR,target_id VARCHAR,trace_id VARCHAR,created_at TIMESTAMP)");
        jdbc.update("INSERT INTO erp_employee VALUES('tenant-a','e1','EMPLOYEE','manager'),('tenant-a','manager','MANAGER',NULL),('tenant-a','hr','HR',NULL)");
        jdbc.update("INSERT INTO erp_leave_balance VALUES('tenant-a','e1',2026,10,2,0,1)");
        service = new LeaveService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)));
    }

    @Test
    void createFreezesAvailableBalanceAndSameIdempotencyKeyReturnsOneRequest() {
        var identity = new ErpIdentity("tenant-a", "e1", "trace-1");
        var request = new LeaveService.CreateLeaveCommand(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15), "年假", "key-1");

        var first = service.create(identity, request);
        var repeated = service.create(identity, request);

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(service.balance(identity, 2026).availableDays()).isEqualByComparingTo("6.00");
        assertThat(service.balance(identity, 2026).frozenDays()).isEqualByComparingTo("2.00");
    }

    @Test
    void insufficientBalanceDoesNotCreateOrFreezeAnything() {
        var identity = new ErpIdentity("tenant-a", "e1", "trace-2");
        var request = new LeaveService.CreateLeaveCommand(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 25), "年假", "key-2");

        assertThatThrownBy(() -> service.create(identity, request)).isInstanceOf(InsufficientLeaveBalanceException.class);
        assertThat(service.balance(identity, 2026).frozenDays()).isEqualByComparingTo("0.00");
    }

    @Test
    void withdrawReleasesFrozenBalanceOnlyForOwner() {
        var owner = new ErpIdentity("tenant-a", "e1", "trace-3");
        var created = service.create(owner, new LeaveService.CreateLeaveCommand(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15), "年假", "key-3"));

        service.withdraw(owner, created.id());

        assertThat(service.balance(owner, 2026).frozenDays()).isEqualByComparingTo("0.00");
        assertThat(service.find(owner, created.id()).status()).isEqualTo(LeaveStatus.WITHDRAWN);
    }

    @Test
    void managerThenHrApprovalSettlesFrozenBalanceAndWritesAudit() {
        var owner = new ErpIdentity("tenant-a", "e1", "trace-approve");
        var created = service.create(owner, new LeaveService.CreateLeaveCommand(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15), "年假", "key-approve"));

        service.managerApprove(new ErpIdentity("tenant-a", "manager", "trace-manager"), created.id(), true);
        service.hrApprove(new ErpIdentity("tenant-a", "hr", "trace-hr"), created.id(), true);

        assertThat(service.find(owner, created.id()).status()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(service.balance(owner, 2026).frozenDays()).isEqualByComparingTo("0.00");
        assertThat(service.balance(owner, 2026).usedDays()).isEqualByComparingTo("4.00");
    }

    @Test
    void concurrentRequestsCannotFreezeMoreThanAvailableBalance() throws Exception {
        var identity = new ErpIdentity("tenant-a", "e1", "trace-4");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var outcomes = executor.invokeAll(java.util.List.<Callable<Boolean>>of(
                    () -> attemptCreate(identity, "concurrent-1"),
                    () -> attemptCreate(identity, "concurrent-2")));
            long successful = outcomes.stream().filter(future -> {
                try { return future.get(); } catch (Exception ignored) { return false; }
            }).count();
            assertThat(successful).isEqualTo(1);
            assertThat(service.balance(identity, 2026).frozenDays()).isEqualByComparingTo("6.00");
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean attemptCreate(ErpIdentity identity, String key) {
        try {
            service.create(identity, new LeaveService.CreateLeaveCommand(
                    LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21), "年假", key));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
