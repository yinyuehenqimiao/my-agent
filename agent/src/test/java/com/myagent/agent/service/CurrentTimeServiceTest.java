package com.myagent.agent.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentTimeServiceTest {

    @Test
    void shouldReturnPinnedAsiaShanghaiTimeInsteadOfModelGeneratedTime() {
        CurrentTimeService service = new CurrentTimeService(
                Clock.fixed(Instant.parse("2026-09-15T10:12:13Z"), ZoneOffset.UTC));

        assertThat(service.describe())
                .isEqualTo("当前时间是 2026年9月15日 星期二 18:12:13（北京时间）。");
    }
}
