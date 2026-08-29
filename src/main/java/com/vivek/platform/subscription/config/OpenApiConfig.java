package com.vivek.platform.subscription.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI subscriptionPlatformOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Subscription & Billing Platform API")
                        .version("v1")
                        .description("""
                                Multi-tenant subscription management and usage-based billing.

                                All endpoints except the docs and the public actuator endpoints
                                require a Keycloak-issued bearer token. Organization-scoped
                                endpoints additionally require the token to claim membership of
                                the organization (`org_id` / `org_ids`), unless the caller holds
                                the `PLATFORM_ADMIN` realm role.""")
                        .contact(new Contact().name("Vivek Kumar"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Keycloak access token")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
