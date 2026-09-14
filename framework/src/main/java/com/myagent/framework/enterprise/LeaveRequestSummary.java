package com.myagent.framework.enterprise;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 当前员工本人可查看的请假单摘要。 */
public record LeaveRequestSummary(
        String requestId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal leaveDays,
        String status) {
}
