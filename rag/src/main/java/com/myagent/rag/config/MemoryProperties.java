package com.myagent.rag.config;

import com.myagent.rag.config.validation.ValidMemoryConfig;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag.memory")
@Validated
@ValidMemoryConfig
public class MemoryProperties {

    @Min(1)
    @Max(100)
    private Integer historyKeepTurns = 8;

    private Boolean summaryEnabled = false;

    private Integer summaryStartTurns = 9;

    @Min(200)
    @Max(1000)
    private Integer summaryMaxChars = 200;

    @Min(10)
    @Max(100)
    private Integer titleMaxLength = 30;
}
