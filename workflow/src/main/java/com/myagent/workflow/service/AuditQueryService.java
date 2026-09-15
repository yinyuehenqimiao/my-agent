package com.myagent.workflow.service;

import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 按当前租户聚合平台、权限、工具和企业业务审计。 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final JdbcTemplate jdbc;

    public List<AuditEntry> list(ExecutionIdentity identity, Category requestedCategory, int limit) {
        PageResult page = page(identity, requestedCategory, 1, limit);
        return page.records();
    }

    public PageResult page(ExecutionIdentity identity, Category requestedCategory, long current, long size) {
        if (identity == null || identity.getTenantId() == null || identity.getTenantId().isBlank()) {
            throw new ClientException("缺少租户身份，拒绝查询审计记录");
        }
        Category category = requestedCategory == null ? Category.ALL : requestedCategory;
        long safeCurrent = Math.max(1L, current);
        long safeSize = Math.min(Math.max(size, 1L), 200L);
        // 先拉窗口，再内存分页；跨表合并后按时间排序。
        int fetchLimit = (int) Math.min(500L, Math.max(safeCurrent * safeSize, safeSize));
        String tenantId = identity.getTenantId();
        List<AuditEntry> entries = new ArrayList<>();

        if (category == Category.ALL || category == Category.PLATFORM) {
            entries.addAll(queryOperationAudit(tenantId, fetchLimit, false));
        }
        if (category == Category.ALL || category == Category.TOOL) {
            entries.addAll(queryOperationAudit(tenantId, fetchLimit, true));
        }
        if (category == Category.ALL || category == Category.AUTHORIZATION) {
            entries.addAll(queryAuthorizationAudit(tenantId, fetchLimit));
        }
        if (category == Category.ALL || category == Category.BUSINESS) {
            entries.addAll(queryBusinessAudit(tenantId, fetchLimit));
        }

        List<AuditEntry> sorted = withUsernames(entries.stream()
                .sorted(Comparator.comparing(AuditEntry::createdTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .toList());
        long total = sorted.size();
        int from = (int) Math.min((safeCurrent - 1L) * safeSize, total);
        int to = (int) Math.min(from + safeSize, total);
        return new PageResult(sorted.subList(from, to), total, safeCurrent, safeSize);
    }

    /** 精确返回同一请求链路的全部跨域审计事件，不受后台列表分页窗口影响。 */
    public List<AuditEntry> byTrace(ExecutionIdentity identity, String traceId) {
        if (identity == null || identity.getTenantId() == null || identity.getTenantId().isBlank()) {
            throw new ClientException("缺少租户身份，拒绝查询审计记录");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new ClientException("缺少 traceId，拒绝查询审计链路");
        }
        String tenantId = identity.getTenantId();
        List<AuditEntry> entries = new ArrayList<>();
        entries.addAll(queryOperationAuditByTrace(tenantId, traceId, false));
        entries.addAll(queryOperationAuditByTrace(tenantId, traceId, true));
        entries.addAll(jdbc.query("""
                        SELECT CAST(id AS VARCHAR) AS audit_id, user_id, username, perm_code, action, reason,
                               trace_id, create_time
                          FROM sys_auth_audit
                         WHERE tenant_id = ? AND trace_id = ?
                        """, (rs, rowNum) -> new AuditEntry(
                        rs.getString("audit_id"), Category.AUTHORIZATION,
                        "AUTH_" + rs.getString("action"), rs.getString("user_id"), rs.getString("username"),
                        "permission", rs.getString("perm_code"), rs.getString("trace_id"),
                        rs.getString("reason"), rs.getTimestamp("create_time")), tenantId, traceId));
        entries.addAll(jdbc.query("""
                        SELECT id, request_id, actor_id, action_id, event, comment, trace_id, created_at
                          FROM ent_audit
                         WHERE tenant_id = ? AND trace_id = ?
                        """, (rs, rowNum) -> new AuditEntry(
                        rs.getString("id"), Category.BUSINESS, rs.getString("event"),
                        rs.getString("actor_id"), null, "leave_request", rs.getString("request_id"),
                        rs.getString("trace_id"), businessDetail(rs.getString("action_id"), rs.getString("comment")),
                        rs.getTimestamp("created_at")), tenantId, traceId));
        return withUsernames(entries.stream()
                .sorted(Comparator.comparing(AuditEntry::createdTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .toList());
    }

    private List<AuditEntry> queryOperationAuditByTrace(String tenantId, String traceId, boolean toolOnly) {
        String operator = toolOnly ? "=" : "<>";
        return jdbc.query("""
                        SELECT CAST(id AS VARCHAR) AS audit_id, operation, operator_id, target_type,
                               target_id, trace_id, detail, created_time
                          FROM operation_audit
                         WHERE tenant_id = ? AND trace_id = ? AND operation %s 'TOOL_EXECUTION'
                        """.formatted(operator),
                (rs, rowNum) -> new AuditEntry(
                        rs.getString("audit_id"), toolOnly ? Category.TOOL : Category.PLATFORM,
                        rs.getString("operation"), rs.getString("operator_id"), null,
                        rs.getString("target_type"), rs.getString("target_id"),
                        rs.getString("trace_id"), rs.getString("detail"),
                        rs.getTimestamp("created_time")), tenantId, traceId);
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
                        rs.getString("operation"), rs.getString("operator_id"), null,
                        rs.getString("target_type"), rs.getString("target_id"),
                        rs.getString("trace_id"), rs.getString("detail"),
                        rs.getTimestamp("created_time")), tenantId, limit);
    }

    private List<AuditEntry> queryAuthorizationAudit(String tenantId, int limit) {
        return jdbc.query("""
                        SELECT CAST(id AS VARCHAR) AS audit_id, user_id, username, perm_code, action, reason,
                               trace_id, create_time
                          FROM sys_auth_audit
                         WHERE tenant_id = ?
                         ORDER BY create_time DESC LIMIT ?
                        """,
                (rs, rowNum) -> new AuditEntry(
                        rs.getString("audit_id"), Category.AUTHORIZATION,
                        "AUTH_" + rs.getString("action"), rs.getString("user_id"), rs.getString("username"),
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
                        rs.getString("actor_id"), null, "leave_request", rs.getString("request_id"),
                        rs.getString("trace_id"), businessDetail(rs.getString("action_id"), rs.getString("comment")),
                        rs.getTimestamp("created_at")), tenantId, limit);
    }

    private List<AuditEntry> withUsernames(List<AuditEntry> entries) {
        Set<String> missing = entries.stream()
                .filter(e -> e.actorName() == null || e.actorName().isBlank())
                .map(AuditEntry::actorId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
        Map<String, String> names = resolveUsernames(missing);
        if (names.isEmpty()) {
            return entries;
        }
        return entries.stream()
                .map(e -> {
                    if (e.actorName() != null && !e.actorName().isBlank()) {
                        return e;
                    }
                    String name = names.get(e.actorId());
                    return name == null ? e : e.withActorName(name);
                })
                .toList();
    }

    /** 按用户 ID（数字字符串）或用户名解析展示名。 */
    public Map<String, String> resolveUsernames(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<String> keys = ids.stream().filter(Objects::nonNull).map(String::trim)
                .filter(s -> !s.isEmpty()).distinct().toList();
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        try {
            String placeholders = keys.stream().map(k -> "?").collect(Collectors.joining(","));
            List<Object> params = new ArrayList<>(keys);
            params.addAll(keys);
            jdbc.query("""
                            SELECT CAST(id AS VARCHAR) AS uid, username, nickname
                              FROM t_user
                             WHERE deleted = 0
                               AND (CAST(id AS VARCHAR) IN (%s) OR username IN (%s))
                            """.formatted(placeholders, placeholders),
                    rs -> {
                        String uid = rs.getString("uid");
                        String username = rs.getString("username");
                        String nickname = rs.getString("nickname");
                        String display = (username != null && !username.isBlank()) ? username
                                : (nickname != null && !nickname.isBlank() ? nickname : uid);
                        if (uid != null) {
                            result.put(uid, display);
                        }
                        if (username != null) {
                            result.put(username, display);
                        }
                    }, params.toArray());
        } catch (RuntimeException ignored) {
            // 测试库或缺表时静默回退为原始 ID。
        }
        return result;
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
            String actorName,
            String targetType,
            String targetId,
            String traceId,
            String detail,
            Timestamp createdTime) {
        public AuditEntry withActorName(String name) {
            return new AuditEntry(id, category, eventCode, actorId, name,
                    targetType, targetId, traceId, detail, createdTime);
        }
    }

    public record PageResult(List<AuditEntry> records, long total, long current, long size) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("records", records);
            m.put("total", total);
            m.put("current", current);
            m.put("size", size);
            return m;
        }
    }
}
