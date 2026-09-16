package com.myagent.erp.organization;

import com.myagent.erp.identity.ErpIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmployeeServiceTest {
    private JdbcTemplate jdbc;
    private EmployeeService service;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:erp-org;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE erp_department(tenant_id VARCHAR,department_id VARCHAR,name VARCHAR,parent_id VARCHAR,PRIMARY KEY(tenant_id,department_id))");
        jdbc.execute("CREATE TABLE erp_employee(tenant_id VARCHAR,employee_id VARCHAR,employee_no VARCHAR,full_name VARCHAR,hire_date DATE,status VARCHAR,department_id VARCHAR,position_name VARCHAR,manager_id VARCHAR,business_role VARCHAR)");
        jdbc.execute("CREATE TABLE erp_employee_permission(tenant_id VARCHAR,employee_id VARCHAR,permission_code VARCHAR,permission_name VARCHAR)");
        jdbc.update("INSERT INTO erp_department VALUES('tenant-a','tech','技术部',NULL),('tenant-b','tech','其他租户技术部',NULL)");
        jdbc.update("INSERT INTO erp_employee VALUES('tenant-a','e1','A001','张三',DATE '2020-09-14','ACTIVE','tech','工程师','m1','EMPLOYEE')");
        jdbc.update("INSERT INTO erp_employee VALUES('tenant-b','e1','B001','其他租户员工',DATE '2018-01-01','ACTIVE','tech','经理',NULL,'MANAGER')");
        jdbc.update("INSERT INTO erp_employee VALUES('tenant-a','left','A002','已离职员工',DATE '2019-01-01','INACTIVE','tech','工程师','m1','EMPLOYEE')");
        jdbc.update("INSERT INTO erp_employee_permission VALUES('tenant-a','e1','leave:self:read','查询本人年假'),('tenant-b','e1','salary:read','查询薪资')");
        service = new EmployeeService(jdbc, new TenureCalculator(),
                Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void profileAndPermissionsAreStrictlyTenantScoped() {
        var identity = new ErpIdentity("tenant-a", "e1", "trace-1");

        assertThat(service.me(identity).fullName()).isEqualTo("张三");
        assertThat(service.permissions(identity)).containsExactly("查询本人年假");
        assertThat(service.tenure(identity).completedYears()).isEqualTo(5);
    }

    @Test
    void inactiveEmployeeCannotUseSelfService() {
        assertThatThrownBy(() -> service.me(new ErpIdentity("tenant-a", "left", "trace-2")))
                .isInstanceOf(EmployeeNotFoundException.class);
    }
}
