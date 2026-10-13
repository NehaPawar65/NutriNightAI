package com.nutrinight.controller;

import com.nutrinight.client.AiServiceClient;
import com.nutrinight.exception.AiServiceException;
import com.nutrinight.exception.AiServiceUnavailableException;
import com.nutrinight.exception.UnprocessableDataException;
import com.nutrinight.service.AiAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiController.class)
@Import(ControllerTestConfig.class)
class AiControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AiAnalysisService aiService;

    @Test
    void classifyReturnsPredictions() throws Exception {
        when(aiService.classify(any())).thenReturn(new AiServiceClient.ClassifyResponse(List.of(
                new AiServiceClient.Prediction("Veg Burger", "FAST_FOOD", "RULE_BASED", null, List.of("burger"),
                        List.of(), List.of(), "Matched keyword(s): burger")), true, "1.0.0"));
        mvc.perform(post("/api/ai/classify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[\"Veg Burger\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predictions[0].method").value("RULE_BASED"))
                .andExpect(jsonPath("$.predictions[0].confidence").isEmpty());
    }

    @Test
    void classifyValidatesInput() throws Exception {
        mvc.perform(post("/api/ai/classify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[\"\"],\"method\":\"magic\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(2));
        mvc.perform(post("/api/ai/classify").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
    }

    @Test
    void aiOutageIs503() throws Exception {
        when(aiService.classify(any())).thenThrow(new AiServiceUnavailableException(
                "AI service is unavailable (/classify): connection failed or timed out", new RuntimeException()));
        mvc.perform(post("/api/ai/classify").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[\"x\"]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("AI service is unavailable (/classify): connection failed or timed out"));
    }

    @Test
    void aiUpstreamErrorIs502() throws Exception {
        when(aiService.analyze(any())).thenThrow(new AiServiceException("AI service returned an error (status 500)", 500));
        mvc.perform(post("/api/ai/analyze").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void noDataIs422AndWeightsAreValidated() throws Exception {
        when(aiService.analyze(any())).thenThrow(new UnprocessableDataException("No orders match the selected period and filters"));
        mvc.perform(post("/api/ai/analyze").contentType(MediaType.APPLICATION_JSON).content("{\"from\":\"2020-01-01\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/ai/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"indicatorWeights\":{\"sugar_per_order\":-1}}"))
                .andExpect(status().isBadRequest());
    }
}
