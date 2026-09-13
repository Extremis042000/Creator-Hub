package com.extremis.hub.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html, raw spec at /v3/api-docs. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hubOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("EXTREMIS Creator Hub API")
                .version("v1")
                .description("Backend API for EXTREMIS Creator Hub's gaming tools."));
    }
}
