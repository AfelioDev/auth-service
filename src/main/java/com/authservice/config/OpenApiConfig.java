package com.authservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Bean
    public OpenAPI authOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Auth Service API")
                        .description("""
                                Authentication microservice for the One Timer platform.

                                Supports two authentication methods:
                                - **Email + password** — register and login with credentials
                                - **WCA OAuth2** — login or register via your World Cube Association account

                                ### Authorization
                                Protected endpoints require a `Bearer` JWT token in the `Authorization` header.
                                Obtain a token via `POST /auth/register`, `POST /auth/login`, or the WCA OAuth2 flow.

                                ### JWT payload
                                ```json
                                { "sub": "userId", "email": "...", "wca_id": "...", "roles": ["USER"] }
                                ```
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("One Timer")
                                .url("https://github.com/AfelioDev")))
                .servers(List.of(new Server().url(baseUrl).description("Current server")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the JWT token obtained from /auth/login or /auth/register")));
    }
}
