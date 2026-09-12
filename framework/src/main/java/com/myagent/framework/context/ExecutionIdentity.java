package com.myagent.framework.context;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 统一执行身份：贯穿 HTTP、SSE 异步线程与未来 Flowable/MCP 上下文。
 * <p>
 * 设计要点：
 * - 同时承载平台角色（RBAC）与企业业务属性（ABAC：部门、企业角色、主管、人事）。
 * - 平台角色与 t_user.role 业务角色分离；企业属性来自企业员工档案。
 * - 默认租户为 'default'，预留多租户扩展。
 * - 模型传入的 userId/tenantId/角色一律不可信，以此服务端签发快照为准。
 */
public interface ExecutionIdentity {

    /** 平台用户 ID（对应 t_user.id） */
    String getUserId();

    /** 用户名 */
    String getUsername();

    /** 旧有业务角色（admin/user），保留兼容现有逻辑 */
    String getRole();

    /** 租户标识，当前固定 'default' */
    String getTenantId();

    /** 平台角色码列表（RBAC），如 PLATFORM_ADMIN */
    List<String> getPlatformRoles();

    /** 部门（ABAC 数据范围） */
    String getDepartment();

    /** 企业业务角色（EMPLOYEE/MANAGER/HR），不混入平台角色 */
    String getEnterpriseRole();

    /** 直属主管 ID */
    String getManagerId();

    /** 人事 ID */
    String getHrId();

    /** 扩展属性，用于未来 Flowable/MCP 上下文附加信息 */
    Map<String, Object> getExtensions();

    /** 是否拥有某平台角色 */
    default boolean hasPlatformRole(String roleCode) {
        return getPlatformRoles() != null && getPlatformRoles().contains(roleCode);
    }

    /** 空身份（用于缺省场景，调用方应做 fail-closed 判断） */
    static ExecutionIdentity empty() {
        return new ExecutionIdentity() {
            @Override public String getUserId() { return null; }
            @Override public String getUsername() { return null; }
            @Override public String getRole() { return null; }
            @Override public String getTenantId() { return "default"; }
            @Override public List<String> getPlatformRoles() { return Collections.emptyList(); }
            @Override public String getDepartment() { return null; }
            @Override public String getEnterpriseRole() { return null; }
            @Override public String getManagerId() { return null; }
            @Override public String getHrId() { return null; }
            @Override public Map<String, Object> getExtensions() { return Collections.emptyMap(); }
        };
    }
}
