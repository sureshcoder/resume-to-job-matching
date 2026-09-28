package in.sureshcoder.resumetojobmatching.jev;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the TypeSafe System One API.
 *
 * <p>There is no official Java SDK for TypeSafe, so this application talks to the API over plain
 * HTTP and implements the retry behaviour that the Python and JavaScript SDKs provide for free.
 */
@ConfigurationProperties(prefix = "typesafe")
public record JevProperties(
        String apiKey,
        String baseUrl,
        String model,
        Duration timeout,
        int maxRetries,
        Duration initialBackoff) {

    public JevProperties {
        baseUrl = baseUrl == null ? "https://api.typesafe.ai" : baseUrl;
        model = model == null ? "jev-latest" : model;
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        maxRetries = maxRetries <= 0 ? 4 : maxRetries;
        initialBackoff = initialBackoff == null ? Duration.ofMillis(500) : initialBackoff;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
