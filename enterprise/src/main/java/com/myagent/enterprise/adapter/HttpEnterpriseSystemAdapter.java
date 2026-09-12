package com.myagent.enterprise.adapter;

import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.enterprise.EmployeeProfile;
import com.myagent.framework.enterprise.EnterpriseSystemPort;
import com.myagent.framework.enterprise.LeaveBalance;
import com.myagent.framework.enterprise.LeaveRequestCommand;
import com.myagent.framework.enterprise.LeaveRequestResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * HTTP ERP 适配器契约（未来对接真实 ERP 时启用）。
 * <p>
 * 启用方式：设置 {@code myagent.enterprise.adapter=HTTP_ERP} 并提供 ERP 连接配置。
 * 当前为契约占位实现：所有方法抛出 {@link UnsupportedOperationException}，
 * 提示尚未接入真实 ERP。实现时应满足：
 * <ul>
 *   <li>身份通过 {@link ExecutionIdentity} 传递，不得使用模型提供的身份字段</li>
 *   <li>写请求强制携带幂等键与 traceId，重复请求返回原结果</li>
 *   <li>分页、错误码、超时、追踪号遵循 ERP OpenAPI 约定</li>
 *   <li>敏感字段脱敏后再返回与审计</li>
 *   <li>ERP 不可用时明确返回失败或处理中，禁止模型编造成功</li>
 * </ul>
 */
@Slf4j
@ConditionalOnProperty(name = "myagent.enterprise.adapter", havingValue = "HTTP_ERP")
public class HttpEnterpriseSystemAdapter implements EnterpriseSystemPort {

    @Override
    public EmployeeProfile getCurrentEmployee(ExecutionIdentity identity) {
        throw new UnsupportedOperationException("HTTP_ERP 适配器尚未接入真实 ERP");
    }

    @Override
    public LeaveBalance getLeaveBalance(ExecutionIdentity identity, int year) {
        throw new UnsupportedOperationException("HTTP_ERP 适配器尚未接入真实 ERP");
    }

    @Override
    public LeaveRequestResult createLeaveRequest(ExecutionIdentity identity, LeaveRequestCommand command) {
        throw new UnsupportedOperationException("HTTP_ERP 适配器尚未接入真实 ERP");
    }
}
