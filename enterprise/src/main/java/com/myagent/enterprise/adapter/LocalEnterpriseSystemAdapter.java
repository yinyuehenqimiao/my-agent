package com.myagent.enterprise.adapter;

import com.myagent.enterprise.dto.EnterpriseTypes.Account;
import com.myagent.enterprise.dto.EnterpriseTypes.Action;
import com.myagent.enterprise.dto.EnterpriseTypes.Employee;
import com.myagent.enterprise.dto.EnterpriseTypes.LeaveInput;
import com.myagent.enterprise.service.EnterpriseService;
import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.enterprise.EmployeeProfile;
import com.myagent.framework.enterprise.EmployeeTenure;
import com.myagent.framework.enterprise.EmployeeBusinessPermissions;
import com.myagent.framework.enterprise.EnterpriseSystemPort;
import com.myagent.framework.enterprise.LeaveBalance;
import com.myagent.framework.enterprise.LeaveRequestCommand;
import com.myagent.framework.enterprise.LeaveRequestResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地企业系统适配器：包装现有 {@link EnterpriseService}，实现 {@link EnterpriseSystemPort}。
 * <p>
 * - 当前单公司运行，直接复用本地服务；未来切换到真实 ERP 时启用 HttpEnterpriseSystemAdapter。
 * - 身份以 {@link ExecutionIdentity} 为准，模型传入的 userId/角色不可信。
 * - 写操作（createLeaveRequest）复用 prepareLeave 的 requestKey 幂等机制：相同 idempotencyKey 返回原结果，不重复创建。
 * - ERP 不可用时抛出明确异常，禁止模型编造成功。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "myagent.enterprise.adapter", havingValue = "LOCAL", matchIfMissing = true)
public class LocalEnterpriseSystemAdapter implements EnterpriseSystemPort {

    private final EnterpriseService service;

    @Override
    public EmployeeProfile getCurrentEmployee(ExecutionIdentity identity) {
        Employee e = service.profile(requireActor(identity));
        return new EmployeeProfile(e.userId(), e.displayName(), e.department(),
                e.businessRole(), e.managerId(), e.hrId(), e.active());
    }

    @Override
    public LeaveBalance getLeaveBalance(ExecutionIdentity identity, int year) {
        Account a = service.balance(requireActor(identity), year);
        return new LeaveBalance(a.userId(), a.year(), a.total(), a.frozen(), a.used());
    }

    @Override
    public EmployeeTenure getCurrentEmployeeTenure(ExecutionIdentity identity) {
        service.profile(requireActor(identity));
        // 旧本地表没有入职日期，明确返回不可用，禁止用账号创建时间猜测司龄。
        return EmployeeTenure.unavailable(LocalDate.now(Clock.systemDefaultZone()));
    }

    @Override
    public EmployeeBusinessPermissions getCurrentEmployeePermissions(ExecutionIdentity identity) {
        Employee employee = service.profile(requireActor(identity));
        List<String> permissions = new ArrayList<>(List.of("查看本人档案", "查询本人年假", "查询本人请假申请", "发起请假"));
        if ("MANAGER".equals(employee.businessRole())) permissions.add("处理直属下属请假审批");
        if ("HR".equals(employee.businessRole())) permissions.add("处理人事请假审批");
        return new EmployeeBusinessPermissions(permissions);
    }

    @Override
    public LeaveRequestResult createLeaveRequest(ExecutionIdentity identity, LeaveRequestCommand command) {
        String actor = requireActor(identity);
        // idempotencyKey 作为 requestKey 复用现有幂等机制：重复请求返回原 Action，不重复创建
        LeaveInput input = new LeaveInput(
                command.getConversationId() == null ? "erp-" + command.getTraceId() : command.getConversationId(),
                command.getIdempotencyKey(),
                command.getLeaveType(),
                LocalDate.parse(command.getStartDate()),
                LocalDate.parse(command.getEndDate()),
                command.getReason());
        Action action = service.prepareLeave(actor, input);
        return new LeaveRequestResult(action.requestId(), "AWAITING_CONFIRMATION", command.getTraceId());
    }

    private String requireActor(ExecutionIdentity identity) {
        if (identity == null || identity.getUserId() == null) {
            throw new com.myagent.framework.exception.ClientException("缺少已认证的执行身份");
        }
        return identity.getUserId();
    }
}
