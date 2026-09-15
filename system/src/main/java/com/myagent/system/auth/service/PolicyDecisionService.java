package com.myagent.system.auth.service;

import com.myagent.framework.context.ExecutionIdentity;

import java.util.List;

/**
 * 权限决策服务（RBAC + ABAC），默认拒绝。
 * <p>
 * - 平台权限码判断走 RBAC（sys_user_role → sys_role_permission）。
 * - 企业数据范围与企业角色判断走 ABAC（ExecutionIdentity 的 department/enterpriseRole）。
 * - 编辑者不得审核自己提交的工作流（自审拦截）。
 * - 系统管理员不自动获得主管或 HR 审批权限。
 * - 所有权限拒绝写入审计记录。
 */
public interface PolicyDecisionService {

    /** 是否拥有某权限码（默认拒绝：身份缺失或无映射即 false） */
    boolean hasPermission(ExecutionIdentity identity, String permCode);

    /** 断言拥有某权限码，否则抛出 ClientException 并写审计 */
    void requirePermission(ExecutionIdentity identity, String permCode);

    /** 断言审核者不是提交者本人（编辑者不得自审） */
    void requireNotSelfReview(ExecutionIdentity reviewer, String submitterId);

    /** 查询用户权限码列表（供 Sa-Token 与缓存使用） */
    List<String> permissionsOf(Long userId);

    /** 查询用户平台角色码列表 */
    List<String> rolesOf(Long userId);

    /** 权限审计持久化失败的单调计数，供健康检查与指标桥接读取。 */
    long auditWriteFailureCount();
}
