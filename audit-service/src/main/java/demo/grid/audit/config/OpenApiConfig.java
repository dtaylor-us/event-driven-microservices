package demo.grid.audit.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI auditEventsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Audit Events API")
                        .version("0.1.0-SNAPSHOT")
                        .description("Read-only API for audited grid events."));
    }
}
