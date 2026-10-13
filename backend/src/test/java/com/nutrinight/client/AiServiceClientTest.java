package com.nutrinight.client;

import com.nutrinight.TestFixtures;
import com.nutrinight.config.NutriNightProperties;
import com.nutrinight.exception.AiServiceException;
import com.nutrinight.exception.AiServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiServiceClientTest {

    private MockRestServiceServer server;
    private AiServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai").defaultHeader("X-API-Key", "k");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiServiceClient(builder.build());
    }

    @Test
    void classifyParsesPredictions() {
        server.expect(requestTo("http://ai/classify")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "k"))
                .andExpect(content().json("{\"items\":[\"Veg Burger\"],\"method\":\"auto\"}"))
                .andRespond(withSuccess("""
                        {"predictions":[{"text":"Veg Burger","category":"FAST_FOOD","method":"RULE_BASED",
                          "confidence":null,"matchedKeywords":["burger"],"topFeatures":[],"alternatives":[],
                          "explanation":"Matched keyword(s): burger"}],"modelAvailable":true,"modelVersion":"1.0.0"}""",
                        MediaType.APPLICATION_JSON));

        AiServiceClient.ClassifyResponse response = client.classify(List.of("Veg Burger"), "auto");

        assertThat(response.predictions()).singleElement().satisfies(p -> {
            assertThat(p.category()).isEqualTo("FAST_FOOD");
            assertThat(p.method()).isEqualTo("RULE_BASED");
            assertThat(p.confidence()).isNull();
        });
        server.verify();
    }

    @Test
    void serverErrorBecomesAiServiceException() {
        server.expect(requestTo("http://ai/analyze")).andRespond(withServerError());
        assertThatThrownBy(() -> client.analyze(Map.of("records", List.of())))
                .isInstanceOf(AiServiceException.class)
                .satisfies(e -> assertThat(((AiServiceException) e).getUpstreamStatus()).isEqualTo(500));
    }

    @Test
    void validationErrorKeepsUpstreamStatus() {
        server.expect(requestTo("http://ai/cluster")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"code\":\"VALIDATION_ERROR\"}}"));
        assertThatThrownBy(() -> client.cluster(Map.of()))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("rejected")
                .satisfies(e -> assertThat(((AiServiceException) e).getUpstreamStatus()).isEqualTo(422));
    }

    @Test
    void malformedJsonBecomesAiServiceException() {
        server.expect(requestTo("http://ai/model-info"))
                .andRespond(withSuccess("{not json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.modelInfo()).isInstanceOf(AiServiceException.class);
    }

    @Test
    void connectionFailureBecomesUnavailable() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        NutriNightProperties p = TestFixtures.properties();
        NutriNightProperties props = new NutriNightProperties(p.timezone(), p.lateNight(), p.privacy(), p.upload(),
                new NutriNightProperties.Ai("http://127.0.0.1:" + freePort, "k", Duration.ofMillis(500),
                        Duration.ofSeconds(1), 10), p.security(), p.cors(), p.nutritionThresholds());
        AiServiceClient real = new AiServiceClient(RestClient.builder(), props);

        assertThatThrownBy(real::health)
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessageContaining("unavailable");
    }
}
