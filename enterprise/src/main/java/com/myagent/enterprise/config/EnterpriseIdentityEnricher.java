package com.myagent.enterprise.config;

import com.myagent.enterprise.dao.mapper.EnterpriseMapper;
import com.myagent.enterprise.dto.EnterpriseTypes.Employee;
import com.myagent.framework.context.LoginUser;
import com.myagent.system.auth.service.ExecutionIdentityEnricher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 企业执行身份富化：从 ent_employee 读取企业业务属性（ABAC）填入 LoginUser。
 * <p>
 * - department / enterpriseRole（EMPLOYEE/MANAGER/HR）/ managerId / hrId。
 * - 员工不存在或停用时，企业属性留空，平台权限不受影响。
 * - 系统管理员不自动获得主管或 HR 审批权限（企业角色仅来自员工档案）。
 */
@Component
@RequiredArgsConstructor
public class EnterpriseIdentityEnricher implements ExecutionIdentityEnricher {

    private final EnterpriseMapper mapper;

    @Override
    public LoginUser enrich(LoginUser user) {
        if (user == null || user.getUserId() == null) {
            return user;
        }
        Employee emp = mapper.employee(user.getUserId(), false);
        if (emp == null || !emp.active()) {
            return user;
        }
        user.setDepartment(emp.department());
        user.setEnterpriseRole(emp.businessRole());
        user.setManagerId(emp.managerId());
        user.setHrId(emp.hrId());
        return user;
    }
}
