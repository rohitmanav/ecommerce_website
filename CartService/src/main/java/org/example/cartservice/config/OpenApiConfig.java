package org.example.cartservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI(@Value("${app.gateway-url}") String gatewayUrl,
                           @Value("${server.port}") String serverPort) {
        return new OpenAPI()
                .info(new Info()
                        .title("Cart Service")
                        .description("Shopping cart of the logged-in user. Every endpoint needs a bearer token.")
                        .version("1.0.0"))
                .servers(List.of(
                        new Server().url(gatewayUrl).description("Through the API gateway"),
                        new Server().url("http://localhost:" + serverPort).description("Direct to this service")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
