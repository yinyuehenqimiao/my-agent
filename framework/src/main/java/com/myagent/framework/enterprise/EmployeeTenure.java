package com.myagent.framework.enterprise;

import java.time.LocalDate;

/**
 * 经企业系统确认的本人入职年限。
 * <p>
 * {@code available=false} 表示当前数据源未维护入职日期；调用方必须明确提示数据暂不可用，
 * 不得根据账号创建时间或模型猜测补全。
 */
public record EmployeeTenure(
        LocalDate hireDate,
        LocalDate asOfDate,
        Integer completedYears,
        boolean available) {

    public static EmployeeTenure unavailable(LocalDate asOfDate) {
        return new EmployeeTenure(null, asOfDate, null, false);
    }
}
