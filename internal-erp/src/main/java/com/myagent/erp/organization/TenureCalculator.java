package com.myagent.erp.organization;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class TenureCalculator {
    public int completedYears(LocalDate hireDate, LocalDate asOfDate) {
        if (hireDate == null || asOfDate == null || hireDate.isAfter(asOfDate)) {
            throw new IllegalArgumentException("入职日期不能晚于统计日期");
        }
        int years = asOfDate.getYear() - hireDate.getYear();
        LocalDate anniversary = hireDate.withYear(asOfDate.getYear());
        return asOfDate.isBefore(anniversary) ? years - 1 : years;
    }
}
