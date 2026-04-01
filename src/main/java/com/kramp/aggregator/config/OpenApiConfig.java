package com.kramp.aggregator.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Product Information Aggregator API",
                version = "1.0",
                description = "Aggregates product catalog, pricing, availability, and customer data "
                        + "from multiple upstream services into a single market-aware response."
        )
)
public class OpenApiConfig {

    @Bean
    public OpenApiCustomizer errorResponseSchema() {
        return openApi -> {
            Schema<?> errorSchema = new Schema<>()
                    .type("object")
                    .addProperty("error", new Schema<>().type("string").example("Not Found"))
                    .addProperty("message", new Schema<>().type("string").example("Product not found: PRD-999"))
                    .addProperty("status", new Schema<>().type("integer").example(404))
                    .addProperty("timestamp", new Schema<>().type("string").example("2025-01-15T10:30:00Z"));

            openApi.getComponents().addSchemas("ErrorResponse", errorSchema);
        };
    }
}
