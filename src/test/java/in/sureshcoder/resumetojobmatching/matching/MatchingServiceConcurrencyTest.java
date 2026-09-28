package in.sureshcoder.resumetojobmatching.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import in.sureshcoder.datafaker.candidate.faker.CandidateFaker;
import in.sureshcoder.datafaker.candidate.model.Candidate;
import in.sureshcoder.datafaker.jobposting.faker.JobFaker;
import in.sureshcoder.datafaker.jobposting.model.JobPosting;
import in.sureshcoder.resumetojobmatching.jev.JevAnswer;
import in.sureshcoder.resumetojobmatching.jev.JevApiException;
import in.sureshcoder.resumetojobmatching.jev.JevClient;
import in.sureshcoder.resumetojobmatching.jev.JevResponse;
import in.sureshcoder.resumetojobmatching.rubric.RubricCatalog;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Jev takes one state per call, so ranking a pool is N calls. These tests pin that the calls
 * overlap rather than queue, and that the overlap stays inside the configured bound.
 */
@SpringBootTest
@ExtendWith(MockitoExtension.class)
class MatchingServiceConcurrencyTest {

    private static final Duration CALL_LATENCY = Duration.ofMillis(200);
    private static final int CANDIDATES = 8;
    private static final int CONCURRENCY = 4;

    @Autowired
    RubricCatalog catalog;

    @Autowired
    StateBuilder stateBuilder;

    @Mock
    JevClient jevClient;

    private MatchingService service;
    private JobPosting posting;
    private List<Candidate> candidates;

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger peakInFlight = new AtomicInteger();

    @BeforeEach
    void setUp() {
        service = new MatchingService(jevClient, catalog, stateBuilder, new MatchingProperties(CONCURRENCY));
        posting = new JobFaker(new Random(3)).jobPosting().buildForIndustry("technology");
        candidates = IntStream.range(0, CANDIDATES)
                .mapToObj(i -> new CandidateFaker(new Random(i)).candidate().buildForIndustry("technology"))
                .toList();
    }

    @Test
    void runsCallsConcurrentlyRatherThanOneAfterAnother() {
        stubWithLatency();

        long startedAt = System.nanoTime();
        List<MatchResult> results = service.rank(posting, candidates, "generic");
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(results).hasSize(CANDIDATES);

        // Sequentially this would take at least CANDIDATES * latency; with a bound of CONCURRENCY
        // it should take roughly (CANDIDATES / CONCURRENCY) * latency. Assert comfortably below the
        // sequential floor so the test is not timing-flaky.
        long sequentialFloorMs = CANDIDATES * CALL_LATENCY.toMillis();
        assertThat(elapsedMs).isLessThan(sequentialFloorMs / 2);
    }

    @Test
    void neverExceedsTheConfiguredConcurrency() {
        stubWithLatency();

        service.rank(posting, candidates, "generic");

        assertThat(peakInFlight.get()).isPositive().isLessThanOrEqualTo(CONCURRENCY);
    }

    @Test
    void returnsAnEmptyRankingWithoutCallingJev() {
        assertThat(service.rank(posting, List.of(), "generic")).isEmpty();
    }

    /**
     * A partial ranking would be misleading: a candidate missing because of a transient upstream
     * failure is indistinguishable from one who simply scored badly.
     */
    @Test
    void failsTheWholeRankingIfAnyCandidateFails() {
        when(jevClient.evaluate(any(), any())).thenAnswer(invocation -> {
            throw new JevApiException("upstream exploded", 529, null);
        });

        assertThatThrownBy(() -> service.rank(posting, candidates, "generic"))
                .isInstanceOf(JevApiException.class)
                .hasMessageContaining("upstream exploded");
    }

    private void stubWithLatency() {
        when(jevClient.evaluate(any(), any())).thenAnswer(invocation -> {
            peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                Thread.sleep(CALL_LATENCY);
                return response();
            } finally {
                inFlight.decrementAndGet();
            }
        });
    }

    private JevResponse response() {
        Map<String, String> legend = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            legend.put(String.valueOf(i), "level-" + i);
        }
        Map<String, JevAnswer> answers = new LinkedHashMap<>();
        List.of("skills_coverage", "experience_depth", "role_relevance", "education_fit")
                .forEach(key -> answers.put(key, new JevAnswer.Score("score", 3.0, legend, Map.of(), 0.95)));
        answers.put("location_compatible", new JevAnswer.Noul("noul", 0.95));
        return new JevResponse("jev-1.13.0", answers, new JevResponse.Usage(300, 40));
    }
}
