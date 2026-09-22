package com.example.paginationdemo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paginationDemoOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spring Boot 3.x Pagination Mastery Demo")
                        .description("Offset (Page/Slice), Keyset/Scroll (Window), custom countQuery, " +
                                "and Specification-based pagination, all on a 100k-row H2 dataset.")
                        .version("1.0.0"));
    }
}
