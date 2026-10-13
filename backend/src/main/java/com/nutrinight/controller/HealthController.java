package com.nutrinight.controller;

import com.nutrinight.service.AiAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@Tag(name = "Health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final AiAnalysisService aiService;

    public HealthController(JdbcTemplate jdbcTemplate, AiAnalysisService aiService) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiService = aiService;
    }

    @GetMapping("/api/health")
    @Operation(summary = "Backend, database and AI-service status")
    public Map<String, Object> health() {
        boolean db;
        try {
            db = Integer.valueOf(1).equals(jdbcTemplate.queryForObject("SELECT 1", Integer.class));
        } catch (RuntimeException e) {
            db = false;
        }
        boolean ai = aiService.isReachable();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", db ? (ai ? "UP" : "DEGRADED") : "DOWN");
        body.put("database", db ? "UP" : "DOWN");
        body.put("aiService", ai ? "UP" : "DOWN");
        body.put("timestamp", Instant.now());
        body.put("version", "1.0.0");
        return body;
    }

    /** Lets the UI verify admin credentials (requires HTTP Basic). */
    @GetMapping("/api/auth/me")
    @Operation(summary = "Return the authenticated user's name and roles")
    public Map<String, Object> me(Authentication authentication) {
        return Map.of("username", authentication.getName(),
                "roles", authentication.getAuthorities().stream().map(Object::toString).toList());
    }
}
