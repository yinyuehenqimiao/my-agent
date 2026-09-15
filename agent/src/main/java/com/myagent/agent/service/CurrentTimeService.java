package com.myagent.agent.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 提供企业助手使用的确定性中国标准时间，避免模型根据训练上下文猜测日期。 */
@Service
public class CurrentTimeService {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern(
            "yyyy年M月d日 EEEE HH:mm:ss", Locale.SIMPLIFIED_CHINESE);

    private final Clock clock;

    @Autowired
    public CurrentTimeService() {
        this(Clock.system(BUSINESS_ZONE));
    }

    CurrentTimeService(Clock clock) {
        this.clock = clock;
    }

    public String describe() {
        return "当前时间是 " + ZonedDateTime.now(clock).withZoneSameInstant(BUSINESS_ZONE)
                .format(DISPLAY_FORMAT) + "（北京时间）。";
    }
}
