package com.nutrinight.controller;

import com.nutrinight.TestFixtures;
import com.nutrinight.config.NutriNightProperties;
import com.nutrinight.security.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Real security rules plus test properties (admin password "secret") for @WebMvcTest slices. */
@TestConfiguration
@Import(SecurityConfig.class)
public class ControllerTestConfig {

    @Bean
    NutriNightProperties nutriNightProperties() {
        return TestFixtures.properties();
    }
}
