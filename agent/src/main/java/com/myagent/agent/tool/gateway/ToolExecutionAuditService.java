package com.myagent.agent.tool.gateway;

import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.tool.ToolInvocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/** 工具调用持久化审计。只记录身份、来源、阶段和结果，绝不持久化模型输入或工具输出。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolExecutionAuditService {

    private final JdbcTemplate jdbc;
    private final AtomicLong writeFailures = new AtomicLong();

    public boolean record(ToolInvocation invocation, String traceId, String phase, String outcome, long elapsedMs) {
        ExecutionIdentity identity = invocation.getIdentity();
        String tenantId = identity == null || identity.getTenantId() == null || identity.getTenantId().isBlank()
                ? "default" : identity.getTenantId();
        String source = invocation.getSource() == null ? "UNKNOWN" : invocation.getSource().name();
        String detail = "phase=" + safeToken(phase) + ",source=" + source
                + ",outcome=" + safeToken(outcome) + ",elapsedMs=" + Math.max(0L, elapsedMs);
        try {
            jdbc.update("""
                            INSERT INTO operation_audit
                                (operation,operator_id,target_type,target_id,trace_id,tenant_id,detail,created_time)
                            VALUES('TOOL_EXECUTION',?,?,?,?,?,?,CURRENT_TIMESTAMP)
                            """,
                    identity == null ? null : identity.getUserId(), "tool", invocation.getToolName(),
                    traceId, tenantId, detail);
            return true;
        } catch (Exception ex) {
            long failures = writeFailures.incrementAndGet();
            log.error("tool_audit_write_failure count={} tool={} traceId={} phase={} error={}",
                    failures, invocation.getToolName(), traceId, phase, ex.getMessage());
            return false;
        }
    }

    public long writeFailureCount() {
        return writeFailures.get();
    }

    private static String safeToken(String value) {
        if (value == null || value.isBlank()) return "UNKNOWN";
        return value.replaceAll("[^A-Za-z0-9_-]", "_").substring(0, Math.min(value.length(), 64));
    }
}
