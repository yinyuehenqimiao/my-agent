package com.myagent.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Utf8ConfigurationTest {

    @Test
    void buildJvmAndRuntimeConsoleUseUtf8() throws Exception {
        assertThat(System.getProperty("file.encoding")).isEqualToIgnoringCase(StandardCharsets.UTF_8.name());
        assertThat(System.getProperty("stdout.encoding")).isEqualToIgnoringCase(StandardCharsets.UTF_8.name());
        assertThat(System.getProperty("stderr.encoding")).isEqualToIgnoringCase(StandardCharsets.UTF_8.name());

        var properties = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"))
                .get(0);
        assertThat(properties.getProperty("logging.charset.console")).isEqualTo("UTF-8");
        assertThat(properties.getProperty("logging.charset.file")).isEqualTo("UTF-8");
    }
}
