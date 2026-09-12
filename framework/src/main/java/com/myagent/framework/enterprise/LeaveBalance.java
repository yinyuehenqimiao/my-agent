package com.myagent.framework.enterprise;

/**
 * 年假余额（企业系统端口 DTO）。
 */
public record LeaveBalance(
        String userId,
        int year,
        int total,
        int frozen,
        int used) {
    public int getAvailable() { return total - frozen - used; }
}
