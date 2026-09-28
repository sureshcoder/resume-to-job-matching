package in.sureshcoder.resumetojobmatching.jev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class JevClientTest {

    private static final String SCORE_RESPONSE =
            """
            {
              "model": "jev-1.13.0",
              "answers": {
                "skills_coverage": {
                  "type": "score",
                  "score": 3.2,
                  "legend": {"0": "none", "1": "one", "2": "some", "3": "most", "4": "all"},
                  "probabilities": {"0": 0.0, "1": 0.1, "2": 0.1, "3": 0.4, "4": 0.4},
                  "confidence": 0.81
                },
                "location_compatible": {"type": "noul", "noul": 0.93}
              },
              "usage": {"input_tokens": 312, "output_tokens": 48}
            }
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private JevProperties properties;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://api.typesafe.ai");
        server = MockRestServiceServer.bindTo(builder).build();
        properties = new JevProperties("test-key", null, null, Duration.ofSeconds(5), 3, Duration.ofMillis(1));
    }

    private JevClient client() {
        return new JevClient(
                builder.defaultHeader("Authorization", "Bearer test-key").build(),
                properties,
                new ObjectMapper());
    }

    @Test
    void sendsTheDocumentedRequestShapeAndParsesBothAnswerTypes() {
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("jev-latest"))
                .andExpect(jsonPath("$.state.candidate.skills[0]").value("Java"))
                .andExpect(jsonPath("$.questions.skills_coverage.type").value("score"))
                // A score question's criteria is an ordered array; a noul's is an object.
                .andExpect(jsonPath("$.questions.skills_coverage.criteria[0]").value("none"))
                .andExpect(jsonPath("$.questions.location_compatible.criteria.true").value("ok"))
                .andRespond(withSuccess(SCORE_RESPONSE, MediaType.APPLICATION_JSON));

        JevResponse response = client()
                .evaluate(
                        Map.of("candidate", Map.of("skills", List.of("Java"))),
                        Map.of(
                                "skills_coverage",
                                JevQuestion.score("cover?", List.of("none", "one", "some", "most", "all")),
                                "location_compatible",
                                JevQuestion.noul("here?", Map.of("true", "ok", "false", "no"))));

        server.verify();
        assertThat(response.usage().inputTokens()).isEqualTo(312);

        JevAnswer.Score score = (JevAnswer.Score) response.answers().get("skills_coverage");
        assertThat(score.score()).isEqualTo(3.2);
        assertThat(score.certainty()).isEqualTo(0.81);
        // 5 levels means the raw score runs 0-4, so 3.2 normalises to 0.8.
        assertThat(score.normalised()).isEqualTo(0.8);

        JevAnswer.Noul noul = (JevAnswer.Noul) response.answers().get("location_compatible");
        assertThat(noul.isTrue()).isTrue();
        // A noul carries no distribution, so certainty is its distance from a coin flip.
        assertThat(noul.certainty()).isCloseTo(0.86, org.assertj.core.data.Offset.offset(1e-9));
    }

    /**
     * Jev speaks snake_case; our own API speaks camelCase. Reading one and writing the other is
     * exactly the kind of thing an annotation change breaks silently, so both directions are
     * pinned here.
     */
    @Test
    void readsJevSnakeCaseUsageButSerialisesItAsCamelCase() {
        ObjectMapper mapper = new ObjectMapper();

        JevResponse.Usage parsed = mapper.readValue(
                "{\"input_tokens\": 312, \"output_tokens\": 48}", JevResponse.Usage.class);
        assertThat(parsed.inputTokens()).isEqualTo(312);
        assertThat(parsed.outputTokens()).isEqualTo(48);

        assertThat(mapper.writeValueAsString(parsed))
                .contains("inputTokens")
                .doesNotContain("input_tokens");
    }

    @Test
    void retriesOnOverloadedThenSucceeds() {
        server.expect(ExpectedCount.once(), requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withRawStatus(529));
        server.expect(ExpectedCount.once(), requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withSuccess(SCORE_RESPONSE, MediaType.APPLICATION_JSON));

        JevResponse response = client()
                .evaluate(Map.of("candidate", Map.of("skills", List.of("Java"))), someQuestions());

        server.verify();
        assertThat(response.model()).isEqualTo("jev-1.13.0");
    }

    @Test
    void doesNotRetryAValidationFailure() {
        server.expect(ExpectedCount.once(), requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body("{\"error\":\"bad criteria\"}"));

        assertThatThrownBy(() -> client().evaluate(Map.of("a", "b"), someQuestions()))
                .isInstanceOf(JevApiException.class)
                .hasMessageContaining("422");

        server.verify();
    }

    @Test
    void givesUpAfterMaxRetries() {
        for (int i = 0; i <= properties.maxRetries(); i++) {
            server.expect(ExpectedCount.once(), requestTo("https://api.typesafe.ai/v1/systemone"))
                    .andRespond(withServerError());
        }

        assertThatThrownBy(() -> client().evaluate(Map.of("a", "b"), someQuestions()))
                .isInstanceOf(JevApiException.class);

        server.verify();
    }

    private Map<String, JevQuestion> someQuestions() {
        return Map.of("skills_coverage", JevQuestion.score("cover?", List.of("none", "all")));
    }
}
