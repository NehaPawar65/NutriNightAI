package com.nutrinight.config;

import com.nutrinight.service.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Recomputes stored recommendations at startup so they always reflect the current comparison rules. */
@Component
public class StartupTasks {

    private static final Logger log = LoggerFactory.getLogger(StartupTasks.class);

    private final RecommendationService recommendationService;

    public StartupTasks(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshRecommendations() {
        try {
            recommendationService.refreshAll();
        } catch (RuntimeException e) {
            log.warn("Could not refresh recommendations at startup: {}", e.getMessage());
        }
    }
}
