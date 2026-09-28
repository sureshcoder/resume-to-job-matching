package in.sureshcoder.resumetojobmatching.matching;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning for ranking fan-out.
 *
 * <p>Jev evaluates one state per call, so scoring a pool of candidates means one call each. The
 * only lever is how many run at once, and the ceiling is TypeSafe's published 1,200 requests per
 * minute — shared across everything this application sends, not per request.
 */
@ConfigurationProperties(prefix = "matching")
public record MatchingProperties(int concurrency) {

    public MatchingProperties {
        concurrency = concurrency <= 0 ? 8 : concurrency;
    }
}
