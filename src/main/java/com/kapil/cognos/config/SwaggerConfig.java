package com.kapil.cognos.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI tenantManagerOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Cognos Tenant Manager API")
                .version("1.0.0")
                .description("Manage Cognos tenants, users, groups, and folders via Java SDK"));
    }
}