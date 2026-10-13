package com.nutrinight.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutrinight.config.NutriNightProperties;
import com.nutrinight.dto.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Stateless HTTP Basic security. Read-only analytics are public; data-changing operations
 * (CSV upload, deleting orders) require the ADMIN role.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // stateless API without cookies
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/orders/upload").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                        .requestMatchers("/api/auth/**").authenticated()
                        .anyRequest().permitAll())
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, ex) ->
                        writeError(objectMapper, response, HttpServletResponse.SC_UNAUTHORIZED,
                                "Admin credentials are required for this action", request.getRequestURI())))
                .exceptionHandling(e -> e.accessDeniedHandler((request, response, ex) ->
                        writeError(objectMapper, response, HttpServletResponse.SC_FORBIDDEN,
                                "You do not have permission for this action", request.getRequestURI())));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(NutriNightProperties properties, PasswordEncoder encoder) {
        String password = properties.security().adminPassword();
        if (password == null || password.isBlank()) {
            // Never fall back to a well-known password: generate a random one so admin actions stay locked.
            password = UUID.randomUUID().toString();
            log.warn("ADMIN_PASSWORD is not set; admin-only endpoints (upload/delete) are effectively disabled.");
        }
        return new InMemoryUserDetailsManager(User.withUsername(properties.security().adminUsername())
                .password(encoder.encode(password))
                .roles("ADMIN")
                .build());
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(NutriNightProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = properties.cors().allowedOrigins() == null ? List.of() :
                properties.cors().allowedOrigins().stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static void writeError(ObjectMapper mapper, HttpServletResponse response, int status,
                                   String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // No WWW-Authenticate header: avoids the browser's native login popup for the SPA.
        mapper.writeValue(response.getOutputStream(), ApiError.of(status, message, path));
    }
}
