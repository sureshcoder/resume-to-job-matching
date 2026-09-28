package in.sureshcoder.resumetojobmatching.matching;

import in.sureshcoder.datafaker.candidate.model.Candidate;
import in.sureshcoder.datafaker.jobposting.model.JobPosting;
import in.sureshcoder.resumetojobmatching.jev.JevAnswer;
import in.sureshcoder.resumetojobmatching.jev.JevClient;
import in.sureshcoder.resumetojobmatching.jev.JevResponse;
import in.sureshcoder.resumetojobmatching.rubric.Rubric;
import in.sureshcoder.resumetojobmatching.rubric.RubricCatalog;
import in.sureshcoder.resumetojobmatching.rubric.RubricQuestion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Scores candidates against a posting using the composite-scoring pattern: ask Jev a batch of
 * atomic questions, then combine the answers with weights that live in this code rather than in
 * the model.
 *
 * <p>Keeping the weights here is the point of the pattern. The same Jev response can be re-weighted
 * for a different role shape — an individual contributor opening and a management opening value the
 * same dimensions differently — without another API call.
 */
@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);
    private static final double AUTO_ADVANCE_CONFIDENCE = 0.9d;
    private static final double HUMAN_REVIEW_CONFIDENCE = 0.5d;

    private final JevClient jevClient;
    private final RubricCatalog catalog;
    private final StateBuilder stateBuilder;

    /**
     * Bounds Jev calls in flight across every concurrent ranking, not per request, because the
     * rate limit that matters is the account's.
     */
    private final Semaphore inFlight;

    public MatchingService(
            JevClient jevClient,
            RubricCatalog catalog,
            StateBuilder stateBuilder,
            MatchingProperties properties) {
        this.jevClient = jevClient;
        this.catalog = catalog;
        this.stateBuilder = stateBuilder;
        this.inFlight = new Semaphore(properties.concurrency());
    }

    public MatchResult match(JobPosting posting, Candidate candidate) {
        return match(posting, candidate, null);
    }

    public MatchResult match(JobPosting posting, Candidate candidate, String rubricId) {
        RubricCatalog.ResolvedRubric resolved =
                rubricId == null ? catalog.forIndustry(posting.industry()) : catalog.byId(rubricId);
        Rubric rubric = resolved.rubric();

        JevResponse response =
                jevClient.evaluate(stateBuilder.build(posting, candidate), rubric.toJevQuestions());

        List<MatchResult.DimensionScore> dimensions = scoreDimensions(rubric, response);
        List<MatchResult.GateResult> gates = evaluateGates(rubric, response);

        double composite = dimensions.stream()
                .mapToDouble(MatchResult.DimensionScore::contribution)
                .sum();
        double confidence = aggregateConfidence(dimensions);

        return new MatchResult(
                "%s %s".formatted(candidate.firstName(), candidate.lastName()),
                headline(candidate),
                posting.title(),
                posting.industry(),
                resolved.id(),
                resolved.fallback(),
                round(composite),
                round(confidence),
                decide(confidence, gates),
                dimensions,
                gates,
                response.usage());
    }

    /**
     * Scores many candidates against one posting, best first.
     *
     * <p>Each candidate is a separate Jev call — the API takes one state per request, so a pool
     * cannot be batched server-side. The calls are pure I/O wait, so they run on virtual threads
     * and overlap instead of queueing, bounded by {@code matching.concurrency} to stay inside the
     * account's rate limit.
     *
     * <p>Scoring stays independent per candidate, which is the point: a candidate's result must not
     * depend on who else happens to be in the pool.
     */
    public List<MatchResult> rank(JobPosting posting, List<Candidate> candidates, String rubricId) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        long startedAt = System.nanoTime();
        log.info(
                "Ranking {} candidate(s) against '{}' with up to {} concurrent Jev call(s)",
                candidates.size(),
                posting.title(),
                inFlight.availablePermits());

        List<MatchResult> results;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<MatchResult>> futures = candidates.stream()
                    .map(candidate -> executor.<MatchResult>submit(() -> {
                        inFlight.acquire();
                        try {
                            return match(posting, candidate, rubricId);
                        } finally {
                            inFlight.release();
                        }
                    }))
                    .toList();
            results = collect(futures);
        }

        log.info(
                "Ranked {} candidate(s) in {}ms",
                results.size(),
                (System.nanoTime() - startedAt) / 1_000_000L);

        return results.stream()
                .sorted(Comparator.comparingDouble(MatchResult::compositeScore).reversed())
                .toList();
    }

    /**
     * Waits for every candidate and fails the whole ranking if any one of them failed.
     *
     * <p>A partial ranking is worse than no ranking: a candidate missing because of a transient 429
     * looks identical to a candidate who scored badly.
     */
    private List<MatchResult> collect(List<Future<MatchResult>> futures) {
        List<MatchResult> results = new ArrayList<>(futures.size());
        for (Future<MatchResult> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while ranking candidates", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("ranking failed", cause);
            }
        }
        return results;
    }

    private List<MatchResult.DimensionScore> scoreDimensions(Rubric rubric, JevResponse response) {
        List<MatchResult.DimensionScore> dimensions = new ArrayList<>();
        for (Map.Entry<String, RubricQuestion> entry : rubric.scoredQuestions().entrySet()) {
            String key = entry.getKey();
            double weight = entry.getValue().getWeight();
            JevAnswer answer = response.answers().get(key);
            if (!(answer instanceof JevAnswer.Score score)) {
                throw new IllegalStateException(
                        "expected a score answer for dimension '%s' but got %s"
                                .formatted(key, answer == null ? "nothing" : answer.type()));
            }
            double normalised = score.normalised();
            dimensions.add(new MatchResult.DimensionScore(
                    key,
                    round(score.score()),
                    round(normalised),
                    weight,
                    round(normalised * weight),
                    round(score.certainty()),
                    levelLabel(score)));
        }
        return dimensions;
    }

    private List<MatchResult.GateResult> evaluateGates(Rubric rubric, JevResponse response) {
        List<MatchResult.GateResult> gates = new ArrayList<>();
        for (Map.Entry<String, RubricQuestion> entry : rubric.gateQuestions().entrySet()) {
            String key = entry.getKey();
            double minimum = entry.getValue().getMinimum();
            if (response.answers().get(key) instanceof JevAnswer.Noul noul) {
                gates.add(new MatchResult.GateResult(
                        key, round(noul.noul()), minimum, noul.noul() >= minimum));
            }
        }
        return gates;
    }

    /**
     * Weighted mean of per-dimension confidence.
     *
     * <p>Weighting matters: low confidence on a dimension worth 40% of the composite should drag
     * the result toward review harder than the same wobble on a 5% dimension.
     */
    private double aggregateConfidence(List<MatchResult.DimensionScore> dimensions) {
        double totalWeight =
                dimensions.stream().mapToDouble(MatchResult.DimensionScore::weight).sum();
        if (totalWeight == 0d) {
            return 0d;
        }
        return dimensions.stream()
                        .mapToDouble(d -> d.confidence() * d.weight())
                        .sum()
                / totalWeight;
    }

    private ReviewDecision decide(double confidence, List<MatchResult.GateResult> gates) {
        if (gates.stream().anyMatch(gate -> !gate.passed())) {
            return ReviewDecision.GATE_FAILED;
        }
        if (confidence >= AUTO_ADVANCE_CONFIDENCE) {
            return ReviewDecision.AUTO_ADVANCE;
        }
        if (confidence >= HUMAN_REVIEW_CONFIDENCE) {
            return ReviewDecision.FLAG_FOR_REVIEW;
        }
        return ReviewDecision.HUMAN_REVIEW;
    }

    /** The candidate's current designation, or their most recent one if they are not working. */
    private String headline(Candidate candidate) {
        return candidate.jobHistory().stream()
                .reduce((first, second) -> second)
                .map(job -> job.designation())
                .orElse("No job history");
    }

    /** The criteria level nearest the returned score, so a caller can read the score in words. */
    private String levelLabel(JevAnswer.Score score) {
        if (score.legend() == null || score.legend().isEmpty()) {
            return null;
        }
        return score.legend().get(String.valueOf((int) Math.round(score.score())));
    }

    private static double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }
}
