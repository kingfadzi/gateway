package com.example.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Control Plane API")
                        .description("Backend API for App Automated Governance")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Development Team")
                                .email("dev@example.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development server")));
    }
}