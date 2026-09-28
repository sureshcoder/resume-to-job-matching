package in.sureshcoder.resumetojobmatching.jev;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls {@code POST /v1/systemone}.
 *
 * <p>TypeSafe publishes Python and JavaScript SDKs only, so the retry policy their clients apply
 * automatically is implemented here: exponential backoff with jitter on 429 (rate limited) and 529
 * (overloaded). Every other status fails fast, because retrying a 422 will only fail again.
 */
@Component
public class JevClient {

    private static final Logger log = LoggerFactory.getLogger(JevClient.class);
    private static final int OVERLOADED = 529;
    private static final int TOO_MANY_REQUESTS = 429;

    private final RestClient restClient;
    private final JevProperties properties;
    private final ObjectMapper objectMapper;

    public JevClient(RestClient jevRestClient, JevProperties properties, ObjectMapper objectMapper) {
        this.restClient = jevRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public JevResponse evaluate(Object state, Map<String, JevQuestion> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("at least one question is required");
        }
        JevRequest request = new JevRequest(state, properties.model(), questions);
        logRequest(request);

        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt <= properties.maxRetries(); attempt++) {
            long startedAt = System.nanoTime();
            try {
                JevResponse response = restClient
                        .post()
                        .uri("/v1/systemone")
                        .body(request)
                        .retrieve()
                        .body(JevResponse.class);
                if (response == null) {
                    throw new JevApiException("empty response body from Jev", 0, null);
                }
                logResponse(response, startedAt);
                return response;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (!isRetryable(status) || attempt == properties.maxRetries()) {
                    log.warn("Jev error response <- status={} body={}", status, e.getResponseBodyAsString());
                    throw new JevApiException(describe(status, e.getResponseBodyAsString()), status, e);
                }
                log.warn("Jev error response <- status={} body={}", status, e.getResponseBodyAsString());
                lastFailure = new JevApiException(describe(status, e.getResponseBodyAsString()), status, e);
                sleepBeforeRetry(attempt, status);
            } catch (RestClientException e) {
                if (attempt == properties.maxRetries()) {
                    throw new JevApiException("could not reach the Jev API: " + e.getMessage(), 0, e);
                }
                lastFailure = new JevApiException("could not reach the Jev API: " + e.getMessage(), 0, e);
                sleepBeforeRetry(attempt, 0);
            }
        }
        throw lastFailure;
    }

    /**
     * Logs the outgoing call: a one-line summary at INFO, the full payload at DEBUG.
     *
     * <p>The payload is only serialised when DEBUG is on, because doing it unconditionally would
     * pay a full JSON render per call just to throw it away.
     *
     * <p>The state has already had identifying fields stripped by StateBuilder, so what lands in
     * the log is job and skills data rather than candidate PII.
     */
    private void logRequest(JevRequest request) {
        log.info(
                "Jev request -> model={} questions={}",
                request.model(),
                request.questions().keySet());
        if (log.isDebugEnabled()) {
            log.debug("Jev request body: {}", writeAsJson(request));
        }
    }

    private void logResponse(JevResponse response, long startedAt) {
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        if (log.isInfoEnabled()) {
            log.info(
                    "Jev response <- model={} answers={} tokens_in={} tokens_out={} in {}ms",
                    response.model(),
                    summarise(response),
                    response.usage() == null ? 0 : response.usage().inputTokens(),
                    response.usage() == null ? 0 : response.usage().outputTokens(),
                    elapsedMs);
        }
        if (log.isDebugEnabled()) {
            log.debug("Jev response body: {}", writeAsJson(response));
        }
    }

    /** Each answer reduced to its headline value, so an INFO line stays one line. */
    private String summarise(JevResponse response) {
        if (response.answers() == null) {
            return "{}";
        }
        StringBuilder summary = new StringBuilder("{");
        response.answers().forEach((key, answer) -> {
            if (summary.length() > 1) {
                summary.append(", ");
            }
            summary.append(key).append('=');
            switch (answer) {
                case JevAnswer.Score score ->
                    summary.append("%.2f/%.2f".formatted(score.score(), score.certainty()));
                case JevAnswer.Choice choice ->
                    summary.append("%s/%.2f".formatted(choice.choice(), choice.certainty()));
                case JevAnswer.Noul noul -> summary.append("%.2f".formatted(noul.noul()));
            }
        });
        return summary.append('}').toString();
    }

    /** Logging must never be the thing that breaks a call, so serialisation failures are swallowed. */
    private String writeAsJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            return "<unserialisable: " + e.getMessage() + ">";
        }
    }

    private static boolean isRetryable(int status) {
        return status == TOO_MANY_REQUESTS || status == OVERLOADED || status >= 500;
    }

    private static String describe(int status, String body) {
        String hint = switch (status) {
            // The docs say 401, but a missing key actually comes back as 403.
            case 401, 403 -> "invalid or missing TYPESAFE_API_KEY";
            case 422 -> "Jev rejected a question definition (check criteria shape and required fields)";
            case TOO_MANY_REQUESTS -> "rate limited";
            case OVERLOADED -> "Jev is overloaded";
            default -> "unexpected status";
        };
        return "Jev call failed with %d: %s. %s".formatted(status, hint, body == null ? "" : body);
    }

    private void sleepBeforeRetry(int attempt, int status) {
        long base = properties.initialBackoff().toMillis() * (1L << attempt);
        long jitter = ThreadLocalRandom.current().nextLong(base / 2 + 1);
        long delay = base + jitter;
        log.warn("Jev call failed (status {}), retrying attempt {} in {}ms", status, attempt + 1, delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new JevApiException("interrupted while backing off", status, ie);
        }
    }
}
