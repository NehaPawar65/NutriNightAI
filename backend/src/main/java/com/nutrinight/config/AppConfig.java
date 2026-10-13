package com.nutrinight.config;

import com.nutrinight.domain.LateNightWindow;
import com.nutrinight.domain.OrderTimeResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
public class AppConfig {

    @Bean
    public LateNightWindow lateNightWindow(NutriNightProperties properties) {
        return new LateNightWindow(properties.lateNight().start(), properties.lateNight().end());
    }

    @Bean
    public OrderTimeResolver orderTimeResolver(NutriNightProperties properties, LateNightWindow window) {
        return new OrderTimeResolver(ZoneId.of(properties.timezone()), window);
    }

    @Bean
    public OpenAPI nutriNightOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NutriNight AI API")
                        .version("1.0.0")
                        .description("""
                                AI analysis of late-night (22:00-04:00) online food choices and NCD-relevant dietary indicators.
                                Outputs describe observed ordering behaviour and nutritional indicators; they are not diagnoses
                                or individual clinical risk predictions."""))
                .components(new Components().addSecuritySchemes("basicAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")
                                .description("Admin credentials; required only for CSV upload and deleting orders")));
    }
}
