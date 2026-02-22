package demo.grid.ingest.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String API_KEY_SCHEME = "apiKey";

    @Bean
    public OpenAPI eventIngestOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Event Ingest API")
                        .version("0.1.0-SNAPSHOT")
                        .description("Ingest domain events into the grid. Use the Authorize button to set X-API-Key (e.g. dev-key)."))
                .components(new Components()
                        .addSecuritySchemes(API_KEY_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-API-Key")
                                        .description("API key for authentication (default: dev-key)")))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }
}
