package com.myagent.framework.config;

import com.alibaba.ttl.threadpool.TtlExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Web 与线程池基础配置（认证拦截在 system 模块）
 */
@Configuration
public class WebAutoConfiguration implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService streamExecutor() {
        ExecutorService raw = Executors.newCachedThreadPool(new CustomizableThreadFactory("stream-"));
        return TtlExecutors.getTtlExecutorService(raw);
    }
}
