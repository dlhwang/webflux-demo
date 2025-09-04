package com.example.webfluxssedemo.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
    info = @Info(
        title = "SSE Demo API",
        version = "v1",
        description = "Spring WebFlux 기반 SSE 데모 API"
    )
)
@Configuration
public class OpenApiConfig {

}
