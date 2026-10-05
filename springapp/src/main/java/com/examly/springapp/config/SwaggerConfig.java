package com.examly.springapp.config;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;

/**
 * All Swagger / OpenAPI configuration lives here.
 * UI: http://localhost:8080/swagger-ui.html   Spec: http://localhost:8080/v3/api-docs
 */
@Configuration
public class SwaggerConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI supportSphereOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SupportSphere API")
                        .version("1.0.0")
                        .description("Support-ticket management platform. Login with POST /api/login, copy the "
                                + "token and use the Authorize button (Bearer JWT) for protected endpoints.")
                        .contact(new Contact().name("SupportSphere").email("support@SupportSphere.com")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT obtained from POST /api/login")))
                .addTagsItem(new Tag().name("Authentication").description("Registration and login"))
                .addTagsItem(new Tag().name("Tickets").description("Support ticket management"))
                .addTagsItem(new Tag().name("Support Agents").description("Support agent management"))
                .addTagsItem(new Tag().name("Feedback").description("Client feedback"));
    }

    /**
     * Adds the common error responses (400/401/403/500) to every operation that does not
     * already document them, so each endpoint lists the full set of status codes.
     */
    @Bean
    public OperationCustomizer commonErrorResponses() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            addIfAbsent(responses, "400", "Bad Request - validation failed");
            addIfAbsent(responses, "401", "Unauthorized - missing, invalid or expired JWT / bad credentials");
            addIfAbsent(responses, "403", "Forbidden - role or resource ownership not permitted");
            addIfAbsent(responses, "500", "Internal Server Error");
            return operation;
        };
    }

    private static void addIfAbsent(ApiResponses responses, String code, String description) {
        if (!responses.containsKey(code)) {
            responses.addApiResponse(code, new ApiResponse().description(description)
                    .content(new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponseDTO")))));
        }
    }
}
