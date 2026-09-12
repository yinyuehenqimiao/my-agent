package com.myagent.enterprise.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class EnterpriseConfiguration {
    @Bean public Clock enterpriseClock() { return Clock.system(ZoneId.of("Asia/Shanghai")); }
}
