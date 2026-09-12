package com.myagent.framework.enterprise;

/**
 * 请假创建结果（企业系统端口 DTO）。
 */
public record LeaveRequestResult(
        String requestId,
        String status,
        String traceId) {
}
