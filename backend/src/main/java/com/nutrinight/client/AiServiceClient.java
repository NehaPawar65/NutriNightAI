package com.nutrinight.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.nutrinight.config.NutriNightProperties;
import com.nutrinight.exception.AiServiceException;
import com.nutrinight.exception.AiServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.List;
import java.util.function.Supplier;

/**
 * HTTP client for the internal Python AI service. The base URL and API key are server-side configuration
 * and are never exposed to the browser.
 */
@Component
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    private final RestClient restClient;

    @Autowired
    public AiServiceClient(RestClient.Builder builder, NutriNightProperties properties) {
        NutriNightProperties.Ai ai = properties.ai();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(ai.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(ai.readTimeout());
        RestClient.Builder configured = builder.clone()
                .baseUrl(ai.baseUrl())
                .requestFactory(factory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
        if (ai.apiKey() != null && !ai.apiKey().isBlank()) {
            configured.defaultHeader(API_KEY_HEADER, ai.apiKey());
        }
        this.restClient = configured.build();
    }

    /** Visible for tests: wraps an already-configured client. */
    AiServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public JsonNode health() {
        return call("health", () -> restClient.get().uri("/health").retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new AiServiceException("AI service health check failed", res.getStatusCode().value());
                })
                .body(JsonNode.class));
    }

    public ClassifyResponse classify(List<String> items, String method) {
        return post("/classify", new ClassifyRequest(items, method), ClassifyResponse.class);
    }

    public JsonNode analyze(Object request) {
        return post("/analyze", request, JsonNode.class);
    }

    public JsonNode cluster(Object request) {
        return post("/cluster", request, JsonNode.class);
    }

    public JsonNode modelInfo() {
        return call("model-info", () -> restClient.get().uri("/model-info").retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new AiServiceException("AI service model-info failed", res.getStatusCode().value());
                })
                .body(JsonNode.class));
    }

    private <T> T post(String path, Object body, Class<T> type) {
        return call(path, () -> restClient.post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    int status = res.getStatusCode().value();
                    String detail = status == 422 || status == 400
                            ? "AI service rejected the request as invalid"
                            : "AI service returned an error";
                    throw new AiServiceException(detail + " (status " + status + ")", status);
                })
                .body(type));
    }

    private <T> T call(String operation, Supplier<T> supplier) {
        long started = System.nanoTime();
        try {
            T result = supplier.get();
            if (result == null) {
                throw new AiServiceException("AI service returned an empty response for " + operation, 502);
            }
            log.info("AI service call '{}' completed in {} ms", operation, (System.nanoTime() - started) / 1_000_000);
            return result;
        } catch (AiServiceException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new AiServiceUnavailableException("AI service is unavailable (" + operation + "): connection failed or timed out", e);
        } catch (RestClientException e) {
            throw new AiServiceException("AI service response could not be processed (" + operation + ")", 502);
        }
    }

    public record ClassifyRequest(List<String> items, String method) {
    }

    public record ClassifyResponse(List<Prediction> predictions, boolean modelAvailable, String modelVersion) {
    }

    /**
     * @param method     RULE_BASED, ML_MODEL or DEFAULT (no rule matched and no model available)
     * @param confidence model probability; {@code null} for rule-based results
     */
    public record Prediction(String text, String category, String method, Double confidence,
                             List<String> matchedKeywords, List<JsonNode> topFeatures, List<JsonNode> alternatives,
                             String explanation) {
    }
}
