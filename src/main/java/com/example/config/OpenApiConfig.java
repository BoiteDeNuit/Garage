package com.example.config;

import com.example.dto.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Garage API", version = "2.0", description = "Учёт машин и владельцев"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
    @Bean
    public OpenApiCustomizer errorResponses()
    {
        return openApi -> {
            openApi.getComponents().addSchemas("ErrorResponse",
                    ModelConverters.getInstance(true).read(ErrorResponse.class).get("ErrorResponse"));
            Content error = new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")));
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                    operation.getResponses().forEach((code, response) -> {
                        if (code.startsWith("4") || code.startsWith("5"))
                        {
                            response.setContent(error);
                        }
                    })));
        };
    }
}
