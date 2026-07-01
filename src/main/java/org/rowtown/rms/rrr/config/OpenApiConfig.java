package org.rowtown.rms.rrr.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for API documentation.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Race Results Repository API")
                .version("1.0.0")
                .description("""
                    # Race Results Repository API

                    Version-controlled repository for rowing regatta timing data based on the Timing Data Interchange EMF schema.

                    ## Features
                    - **Version Control**: Full history tracking for all documents with EMF Compare-based diffs
                    - **Document Types**: Support for Race Results, Start Lists, and other timing data
                    - **Search**: Powerful search with exact, partial, and wildcard matching
                    - **Authorization**: Role-based access control for regatta data
                    - **Real-time**: Webhook and MQTT support for live updates

                    ## Data Format
                    Documents are stored as EMF (Eclipse Modeling Framework) models in XMI or binary format,
                    conforming to the Timing Data Interchange schema for rowing regattas.

                    ## Authentication
                    Most endpoints require JWT bearer token authentication. Include the token in the Authorization header:
                    ```
                    Authorization: Bearer <your-jwt-token>
                    ```
                    """)
                .contact(new Contact()
                    .name("Race Results Repository Team")
                    .email("support@rowtown.org")
                    .url("https://github.com/rowtown/race-results-repository"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0")))
            .externalDocs(new ExternalDocumentation()
                .description("Timing Data Interchange Schema Documentation")
                .url("https://github.com/rowtown/timing-data-interchange"))
            .servers(List.of(
                new Server()
                    .url("http://localhost:" + serverPort)
                    .description("Local development server"),
                new Server()
                    .url("https://api.rowtown.org")
                    .description("Production server")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT token for authenticating API requests. " +
                            "Tokens should be obtained from the authentication service and include " +
                            "necessary permissions for the regatta and document type being accessed.")));
    }
}
