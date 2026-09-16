package com.myagent.erp.organization;

import com.myagent.erp.identity.ErpIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class EmployeeService {
    private final JdbcTemplate jdbc;
    private final TenureCalculator tenureCalculator;
    private final Clock clock;

    @Autowired
    public EmployeeService(JdbcTemplate jdbc, TenureCalculator tenureCalculator) {
        this(jdbc, tenureCalculator, Clock.systemDefaultZone());
    }

    EmployeeService(JdbcTemplate jdbc, TenureCalculator tenureCalculator, Clock clock) {
        this.jdbc = jdbc;
        this.tenureCalculator = tenureCalculator;
        this.clock = clock;
    }

    public EmployeeProfile me(ErpIdentity identity) {
        List<EmployeeProfile> rows = jdbc.query("""
                SELECT e.employee_id,e.employee_no,e.full_name,e.hire_date,e.status,e.position_name,
                       d.department_id,d.name AS department_name,e.manager_id
                  FROM erp_employee e JOIN erp_department d
                    ON d.tenant_id=e.tenant_id AND d.department_id=e.department_id
                 WHERE e.tenant_id=? AND e.employee_id=? AND e.status='ACTIVE'
                """, (rs, n) -> new EmployeeProfile(rs.getString("employee_id"), rs.getString("employee_no"),
                rs.getString("full_name"), rs.getObject("hire_date", LocalDate.class), rs.getString("status"),
                rs.getString("department_id"), rs.getString("department_name"), rs.getString("position_name"),
                rs.getString("manager_id")), identity.tenantId(), identity.employeeId());
        if (rows.isEmpty()) throw new EmployeeNotFoundException();
        return rows.get(0);
    }

    public Tenure tenure(ErpIdentity identity) {
        EmployeeProfile employee = me(identity);
        LocalDate asOf = LocalDate.now(clock);
        return new Tenure(employee.hireDate(), asOf, tenureCalculator.completedYears(employee.hireDate(), asOf));
    }

    public List<String> permissions(ErpIdentity identity) {
        me(identity);
        return jdbc.queryForList("""
                SELECT permission_name FROM erp_employee_permission
                 WHERE tenant_id=? AND employee_id=? ORDER BY permission_name
                """, String.class, identity.tenantId(), identity.employeeId());
    }

    public record EmployeeProfile(String employeeId, String employeeNo, String fullName, LocalDate hireDate,
                                  String status, String departmentId, String departmentName,
                                  String positionName, String managerId) {}
    public record Tenure(LocalDate hireDate, LocalDate asOfDate, int completedYears) {}
}
