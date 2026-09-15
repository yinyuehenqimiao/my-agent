package com.myagent.agent.controller;

import com.myagent.agent.tool.gateway.ToolExecutionAuditService;
import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.convention.Result;
import com.myagent.framework.web.Results;
import com.myagent.rag.trace.RagTraceAuditService;
import com.myagent.system.auth.service.PolicyDecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 审计持久化健康信号；仅审计员可读，不提供计数清零或绕过阻断的入口。 */
@RestController
@RequiredArgsConstructor
public class AuditHealthController {

    private final RagTraceAuditService reasoningAudit;
    private final ToolExecutionAuditService toolAudit;
    private final PolicyDecisionService policy;

    @GetMapping("/api/admin/audit/health")
    public Result<AuditHealth> health() {
        ExecutionIdentity identity = UserContext.requireIdentity();
        policy.requirePermission(identity, "audit:view");
        long reasoningFailures = reasoningAudit.writeFailureCount();
        long toolFailures = toolAudit.writeFailureCount();
        long securityFailures = policy.auditWriteFailureCount();
        String status = reasoningFailures + toolFailures + securityFailures == 0L ? "UP" : "DEGRADED";
        return Results.success(new AuditHealth(status, reasoningFailures, toolFailures, securityFailures));
    }

    public record AuditHealth(
            String status,
            long reasoningTraceWriteFailures,
            long toolAuditWriteFailures,
            long securityAuditWriteFailures) {
    }
}
