package com.myagent.system.auth.service.impl;

import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 权限决策实现：JdbcTemplate 驱动、默认拒绝、拒绝写审计。
 * <p>
 * 使用 JdbcTemplate 而非 MyBatis-Plus，便于在 H2 测试上下文中装配，
 * 同时生产环境由 Spring Boot 自动配置 JdbcTemplate。
 * 不缓存权限，保证用户停用/角色撤销后实时生效（每次请求实时查库）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyDecisionServiceImpl implements com.myagent.system.auth.service.PolicyDecisionService {

    private final JdbcTemplate jdbc;

    private static final String SQL_PERMS =
            "SELECT DISTINCT rp.perm_code FROM sys_user_role ur "
                    + "JOIN sys_role_permission rp ON ur.role_code = rp.role_code "
                    + "JOIN sys_role r ON ur.role_code = r.role_code AND r.enabled = TRUE "
                    + "JOIN sys_permission p ON rp.perm_code = p.perm_code AND p.enabled = TRUE "
                    + "WHERE ur.user_id = ? AND ur.tenant_id = ?";

    private static final String SQL_ROLES =
            "SELECT ur.role_code FROM sys_user_role ur "
                    + "JOIN sys_role r ON ur.role_code = r.role_code AND r.enabled = TRUE "
                    + "WHERE ur.user_id = ? AND ur.tenant_id = 'default'";

    private final AtomicLong auditWriteFailures = new AtomicLong();

    @Override
    public boolean hasPermission(ExecutionIdentity identity, String permCode) {
        if (identity == null || identity.getUserId() == null) {
            return false; // fail-closed
        }
        Long userId;
        try {
            userId = Long.valueOf(identity.getUserId());
        } catch (NumberFormatException e) {
            return false;
        }
        String tenantId = identity.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }
        return permissionsOf(userId, tenantId).contains(permCode);
    }

    @Override
    public void requirePermission(ExecutionIdentity identity, String permCode) {
        if (!hasPermission(identity, permCode)) {
            auditDeny(identity, permCode, "缺少权限码: " + permCode);
            throw new ClientException("无权限: " + permCode);
        }
    }

    @Override
    public void requireNotSelfReview(ExecutionIdentity reviewer, String submitterId) {
        if (reviewer == null || reviewer.getUserId() == null || submitterId == null) {
            auditDeny(reviewer, "workflow:review", "审核身份或提交者缺失");
            throw new ClientException("审核身份缺失");
        }
        if (reviewer.getUserId().equals(submitterId)) {
            auditDeny(reviewer, "workflow:review", "编辑者不得审核自己提交的工作流");
            throw new ClientException("不得审核自己提交的工作流");
        }
    }

    @Override
    public List<String> permissionsOf(Long userId) {
        return permissionsOf(userId, "default");
    }

    private List<String> permissionsOf(Long userId, String tenantId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        try {
            return jdbc.queryForList(SQL_PERMS, String.class, userId, tenantId);
        } catch (Exception e) {
            // 表不存在等异常时 fail-closed
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> rolesOf(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        try {
            return jdbc.queryForList(SQL_ROLES, String.class, userId);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 记录权限拒绝到审计表；记录失败不影响主流程 */
    private void auditDeny(ExecutionIdentity identity, String permCode, String reason) {
        try {
            jdbc.update(
                    "INSERT INTO sys_auth_audit(user_id,username,tenant_id,perm_code,action,reason,trace_id) "
                            + "VALUES(?,?,?,?,?,?,?)",
                    identity == null ? null : identity.getUserId(),
                    identity == null ? null : identity.getUsername(),
                    identity == null ? "default" : identity.getTenantId(),
                    permCode, "DENY", reason, UUID.randomUUID().toString());
        } catch (Exception e) {
            long failures = auditWriteFailures.incrementAndGet();
            log.error("security_audit_write_failure count={} permCode={} error={}",
                    failures, permCode, e.getMessage());
        }
    }

    /** 供健康检查和指标桥接读取；每次鉴权审计落库失败单调递增。 */
    public long auditWriteFailureCount() {
        return auditWriteFailures.get();
    }
}
