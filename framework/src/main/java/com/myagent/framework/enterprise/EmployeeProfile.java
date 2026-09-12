package com.myagent.framework.enterprise;

/**
 * 员工档案（企业系统端口 DTO）。
 */
public record EmployeeProfile(
        String userId,
        String displayName,
        String department,
        String businessRole,
        String managerId,
        String hrId,
        boolean active) {
}
