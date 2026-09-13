package com.myagent.workflow.service;

import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 按当前租户聚合平台、权限、工具和企业业务审计。 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final JdbcTemplate jdbc;

    public List<AuditEntry> list(ExecutionIdentity identity, Category requestedCategory, int limit) {
        if (identity == null || identity.getTenantId() == null || identity.getTenantId().isBlank()) {
            throw new ClientException("缺少租户身份，拒绝查询审计记录");
        }
        Category category = requestedCategory == null ? Category.ALL : requestedCategory;
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        String tenantId = identity.getTenantId();
        List<AuditEntry> entries = new ArrayList<>();

        if (category == Category.ALL || category == Category.PLATFORM) {
            entries.addAll(queryOperationAudit(tenantId, safeLimit, false));
        }
        if (category == Category.ALL || category == Category.TOOL) {
            entries.addAll(queryOperationAudit(tenantId, safeLimit, true));
        }
        if (category == Category.ALL || category == Category.AUTHORIZATION) {
            entries.addAll(queryAuthorizationAudit(tenantId, safeLimit));
        }
        if (category == Category.ALL || category == Category.BUSINESS) {
            entries.addAll(queryBusinessAudit(tenantId, safeLimit));
        }

        return entries.stream()
                .sorted(Comparator.comparing(AuditEntry::createdTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .limit(safeLimit)
                .toList();
    }

    private List<AuditEntry> queryOperationAudit(String tenantId, int limit, boolean toolOnly) {
        String operator = toolOnly ? "=" : "<>";
        return jdbc.query("""
                        SELECT CAST(id AS VARCHAR) AS audit_id, operation, operator_id, target_type,
                               target_id, trace_id, detail, created_time
                          FROM operation_audit
                         WHERE tenant_id = ? AND operation %s 'TOOL_EXECUTION'
                         ORDER BY created_time DESC LIMIT ?
                        """.formatted(operator),
                (rs, rowNum) -> new AuditEntry(
                        rs.getString("audit_id"), toolOnly ? Category.TOOL : Category.PLATFORM,
                        rs.getString("operation"), rs.getString("operator_id"),
                        rs.getString("target_type"), rs.getString("target_id"),
                        rs.getString("trace_id"), rs.getString("detail"),
                        rs.getTimestamp("created_time")), tenantId, limit);
    }

    private List<AuditEntry> queryAuthorizationAudit(String tenantId, int limit) {
        return jdbc.query("""
                        SELECT CAST(id AS VARCHAR) AS audit_id, user_id, perm_code, action, reason,
                               trace_id, create_time
                          FROM sys_auth_audit
                         WHERE tenant_id = ?
                         ORDER BY create_time DESC LIMIT ?
                        """,
                (rs, rowNum) -> new AuditEntry(
                        rs.getString("audit_id"), Category.AUTHORIZATION,
                        "AUTH_" + rs.getString("action"), rs.getString("user_id"),
                        "permission", rs.getString("perm_code"), rs.getString("trace_id"),
                        rs.getString("reason"), rs.getTimestamp("create_time")), tenantId, limit);
    }

    private List<AuditEntry> queryBusinessAudit(String tenantId, int limit) {
        return jdbc.query("""
                        SELECT id, request_id, actor_id, action_id, event, comment, trace_id, created_at
                          FROM ent_audit
                         WHERE tenant_id = ?
                         ORDER BY created_at DESC LIMIT ?
                        """,
                (rs, rowNum) -> new AuditEntry(
                        rs.getString("id"), Category.BUSINESS, rs.getString("event"),
                        rs.getString("actor_id"), "leave_request", rs.getString("request_id"),
                        rs.getString("trace_id"), businessDetail(rs.getString("action_id"), rs.getString("comment")),
                        rs.getTimestamp("created_at")), tenantId, limit);
    }

    private String businessDetail(String actionId, String comment) {
        if (actionId == null || actionId.isBlank()) return comment;
        if (comment == null || comment.isBlank()) return "actionId=" + actionId;
        return "actionId=" + actionId + "; " + comment;
    }

    public enum Category { ALL, PLATFORM, AUTHORIZATION, TOOL, BUSINESS }

    public record AuditEntry(
            String id,
            Category category,
            String eventCode,
            String actorId,
            String targetType,
            String targetId,
            String traceId,
            String detail,
            Timestamp createdTime) {
    }
}
