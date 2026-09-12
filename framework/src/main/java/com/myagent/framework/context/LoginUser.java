package com.myagent.framework.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 当前登录用户快照，实现统一 {@link ExecutionIdentity}。
 * <p>
 * 兼容说明：
 * - 保留 userId/username/role 三字段与三参构造，兼容现有代码与测试。
 * - 新增平台角色（RBAC）与企业业务属性（ABAC），由 UserContextInterceptor 装配。
 * - 集合字段带初始值，确保无参构造（DataClassRowMapper）与反射场景下不为 null。
 * - 模型传入的身份不可信，以此服务端签发快照为准。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements ExecutionIdentity {

    private String userId;
    private String username;
    private String role;

    /** 租户标识，默认 'default' */
    @Builder.Default
    private String tenantId = "default";

    /** 平台角色码列表（RBAC） */
    @Builder.Default
    private List<String> platformRoles = Collections.emptyList();

    /** 部门（ABAC 数据范围） */
    private String department;

    /** 企业业务角色（EMPLOYEE/MANAGER/HR） */
    private String enterpriseRole;

    /** 直属主管 ID */
    private String managerId;

    /** 人事 ID */
    private String hrId;

    /** 扩展属性 */
    @Builder.Default
    private Map<String, Object> extensions = Collections.emptyMap();

    /** 向后兼容的三参构造（现有测试与代码使用） */
    public LoginUser(String userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.tenantId = "default";
        this.platformRoles = Collections.emptyList();
        this.extensions = Collections.emptyMap();
    }
}
