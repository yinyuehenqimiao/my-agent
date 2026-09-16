package com.myagent.erp.leave;

import com.myagent.erp.api.ApiExceptionHandler;
import com.myagent.erp.identity.ServiceIdentityFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class LeaveControllerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:erp-leave-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE erp_leave_balance(tenant_id VARCHAR,employee_id VARCHAR,leave_year INT,total_days DECIMAL(6,2),used_days DECIMAL(6,2),frozen_days DECIMAL(6,2),version BIGINT,PRIMARY KEY(tenant_id,employee_id,leave_year))");
        jdbc.execute("CREATE TABLE erp_leave_request(id VARCHAR PRIMARY KEY,tenant_id VARCHAR,employee_id VARCHAR,start_date DATE,end_date DATE,leave_days DECIMAL(6,2),reason VARCHAR,status VARCHAR,manager_id VARCHAR,created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE erp_idempotency(caller_id VARCHAR,endpoint VARCHAR,idempotency_key VARCHAR,request_hash VARCHAR,resource_id VARCHAR,PRIMARY KEY(caller_id,endpoint,idempotency_key))");
        jdbc.execute("CREATE TABLE erp_employee(tenant_id VARCHAR,employee_id VARCHAR,business_role VARCHAR,manager_id VARCHAR)");
        jdbc.update("INSERT INTO erp_employee VALUES('tenant-a','e1','EMPLOYEE','manager')");
        jdbc.update("INSERT INTO erp_leave_balance VALUES('tenant-a','e1',2026,10,0,0,1)");
        mvc = standaloneSetup(new LeaveController(new LeaveService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)))))
                .setControllerAdvice(new ApiExceptionHandler()).addFilters(new ServiceIdentityFilter("test-token")).build();
    }

    @Test
    void createThenQueryBalanceUsesChineseBusinessContract() throws Exception {
        mvc.perform(authenticated(post("/api/erp/v1/leave-requests")).header("Idempotency-Key", "create-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startDate\":\"2026-09-14\",\"endDate\":\"2026-09-15\",\"reason\":\"年假\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING_MANAGER"));
        mvc.perform(authenticated(get("/api/erp/v1/leave-balances/me").param("year", "2026")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.availableDays").value(8));
    }

    @Test
    void currentEmployeeCanListOnlyOwnLeaveRequests() throws Exception {
        mvc.perform(authenticated(post("/api/erp/v1/leave-requests")).header("Idempotency-Key", "list-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startDate\":\"2026-09-14\",\"endDate\":\"2026-09-15\",\"reason\":\"年假\"}"))
                .andExpect(status().isOk());

        mvc.perform(authenticated(get("/api/erp/v1/leave-requests/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING_MANAGER"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request.header("X-ERP-Service-Token", "test-token").header("X-Tenant-Id", "tenant-a")
                .header("X-Employee-Id", "e1").header("X-Trace-Id", "trace-leave-api");
    }
}
