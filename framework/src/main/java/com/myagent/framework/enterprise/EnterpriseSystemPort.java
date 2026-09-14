package com.myagent.framework.enterprise;

import com.myagent.framework.context.ExecutionIdentity;

import java.util.List;

/**
 * 企业系统端口：抽象本地企业系统与未来 ERP。
 * <p>
 * - {@link LocalEnterpriseSystemAdapter}（enterprise 模块）包装现有 EnterpriseService。
 * - 未来 {@code HttpEnterpriseSystemAdapter} 通过 HTTP 对接真实 ERP，契约与本接口一致。
 * - 写接口强制携带幂等键与 traceId；ERP 不可用时不允许模型编造成功。
 * - 身份以 {@link ExecutionIdentity} 为准，模型传入的 userId/角色不可信。
 */
public interface EnterpriseSystemPort {

    /** 当前员工档案 */
    EmployeeProfile getCurrentEmployee(ExecutionIdentity identity);

    /** 年度年假余额 */
    LeaveBalance getLeaveBalance(ExecutionIdentity identity, int year);

    /** 当前员工的入职年限；数据源未维护入职日期时返回 {@code available=false}。 */
    EmployeeTenure getCurrentEmployeeTenure(ExecutionIdentity identity);

    /** 当前员工可理解的业务权限名称，禁止直接返回内部权限码。 */
    EmployeeBusinessPermissions getCurrentEmployeePermissions(ExecutionIdentity identity);

    /** 当前员工本人可查看的请假单摘要。 */
    List<LeaveRequestSummary> getMyLeaveRequests(ExecutionIdentity identity);

    /**
     * 创建请假申请（写操作，幂等）。
     * 重复携带相同 idempotencyKey 的请求返回原结果，不重复扣减。
     */
    LeaveRequestResult createLeaveRequest(ExecutionIdentity identity, LeaveRequestCommand command);
}
