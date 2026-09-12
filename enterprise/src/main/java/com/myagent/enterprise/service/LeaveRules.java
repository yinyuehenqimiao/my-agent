package com.myagent.enterprise.service;

import com.myagent.framework.exception.ClientException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/** Deterministic demonstration policy, independent of generated text. */
public final class LeaveRules {
    private LeaveRules() { }
    public static int days(LocalDate start, LocalDate end, Map<LocalDate, Boolean> overrides) {
        if (start == null || end == null || end.isBefore(start)) throw new ClientException("请填写有效的起止日期");
        if (start.getYear() != end.getYear()) throw new ClientException("跨年申请请按年度拆分");
        if (start.getYear() < 2000 || end.getYear() > 2100) throw new ClientException("年份超出支持范围");
        int count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            boolean weekday = d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
            if (overrides.getOrDefault(d, weekday)) count++;
        }
        if (count == 0) throw new ClientException("所选日期不包含工作日");
        return count;
    }
}
