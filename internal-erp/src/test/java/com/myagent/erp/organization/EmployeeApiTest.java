package com.myagent.erp.organization;

import com.myagent.erp.api.ApiExceptionHandler;
import com.myagent.erp.identity.ServiceIdentityFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class EmployeeApiTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:erp-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE erp_department(tenant_id VARCHAR,department_id VARCHAR,name VARCHAR,parent_id VARCHAR)");
        jdbc.execute("CREATE TABLE erp_employee(tenant_id VARCHAR,employee_id VARCHAR,employee_no VARCHAR,full_name VARCHAR,hire_date DATE,status VARCHAR,department_id VARCHAR,position_name VARCHAR,manager_id VARCHAR,business_role VARCHAR)");
        jdbc.execute("CREATE TABLE erp_employee_permission(tenant_id VARCHAR,employee_id VARCHAR,permission_code VARCHAR,permission_name VARCHAR)");
        jdbc.update("INSERT INTO erp_department VALUES('tenant-a','tech','技术部',NULL)");
        jdbc.update("INSERT INTO erp_employee VALUES('tenant-a','e1','A001','张三',DATE '2020-09-14','ACTIVE','tech','工程师','m1','EMPLOYEE')");
        jdbc.update("INSERT INTO erp_employee_permission VALUES('tenant-a','e1','leave:self:read','查询本人年假')");
        var service = new EmployeeService(jdbc, new TenureCalculator());
        mvc = standaloneSetup(new EmployeeController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new ServiceIdentityFilter("test-token"))
                .build();
    }

    @Test
    void meUsesOnlyAuthenticatedHeaderIdentityAndReturnsTrace() throws Exception {
        mvc.perform(authenticated(get("/api/erp/v1/employees/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.fullName").value("张三"))
                .andExpect(jsonPath("$.traceId").value("trace-api"));
    }

    @Test
    void missingServiceCredentialIsRejected() throws Exception {
        mvc.perform(get("/api/erp/v1/employees/me")
                        .header("X-Tenant-Id", "tenant-a").header("X-Employee-Id", "e1").header("X-Trace-Id", "trace-api"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERP_UNAUTHORIZED"));
    }

    @Test
    void permissionsExposeBusinessNamesInsteadOfInternalCodes() throws Exception {
        mvc.perform(authenticated(get("/api/erp/v1/employees/me/permissions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("查询本人年假"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request.header("X-ERP-Service-Token", "test-token")
                .header("X-Tenant-Id", "tenant-a").header("X-Employee-Id", "e1").header("X-Trace-Id", "trace-api");
    }
}
