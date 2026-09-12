package com.myagent.framework.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 文档（springdoc）
 * <p>
 * 访问：http://localhost:18080/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME = "Sa-Token";

    @Bean
    public OpenAPI myAgentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("my-agent API")
                        .description("""
                                学习型 Agent 接口文档。

                                **使用步骤**
                                1. 调用 `POST /api/auth/login` 拿到 token
                                2. 点击右上角 Authorize，填入登录返回的 JWT
                                3. 再测需要登录的接口

                                认证采用 JWT，请求头为 `Authorization: Bearer <JWT>`。
                                """)
                        .version("0.0.1-SNAPSHOT")
                        .contact(new Contact().name("my-agent")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录返回的 JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
