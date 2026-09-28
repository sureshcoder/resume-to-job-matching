package in.sureshcoder.resumetojobmatching.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import in.sureshcoder.datafaker.candidate.faker.CandidateFaker;
import in.sureshcoder.datafaker.candidate.model.Candidate;
import in.sureshcoder.datafaker.jobposting.faker.JobFaker;
import in.sureshcoder.datafaker.jobposting.model.JobPosting;
import in.sureshcoder.resumetojobmatching.jev.JevAnswer;
import in.sureshcoder.resumetojobmatching.jev.JevClient;
import in.sureshcoder.resumetojobmatching.jev.JevResponse;
import in.sureshcoder.resumetojobmatching.rubric.RubricCatalog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Exercises the arithmetic and routing that sit on top of Jev, with the model call stubbed. The
 * point is that the weighting and the confidence gate are ours, so they are testable without a
 * network call or an API key.
 */
@SpringBootTest
@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(0.002d);

    @Autowired
    RubricCatalog catalog;

    @Autowired
    StateBuilder stateBuilder;

    @Mock
    JevClient jevClient;

    private MatchingService service;
    private JobPosting posting;
    private Candidate candidate;

    @BeforeEach
    void setUp() {
        service = new MatchingService(jevClient, catalog, stateBuilder, new MatchingProperties(4));
        posting = new JobFaker(new Random(42)).jobPosting().buildForIndustry("technology");
        candidate = new CandidateFaker(new Random(42)).candidate().buildForIndustry("technology");
    }

    @Test
    void combinesNormalisedDimensionScoresUsingTheRubricWeights() {
        // generic rubric weights: skills .35, experience .25, role .25, education .15
        stub(Map.of(
                "skills_coverage", score(4.0, 0.95),
                "experience_depth", score(2.0, 0.90),
                "role_relevance", score(3.0, 0.92),
                "education_fit", score(0.0, 0.91)),
                Map.of("location_compatible", 0.99));

        MatchResult result = service.match(posting, candidate, "generic");

        // (1.0*.35) + (0.5*.25) + (0.75*.25) + (0.0*.15) = 0.6625
        assertThat(result.compositeScore()).isCloseTo(0.6625d, TOLERANCE);
        assertThat(result.dimensions()).hasSize(4);
        assertThat(result.dimensions().stream().mapToDouble(MatchResult.DimensionScore::contribution).sum())
                .isCloseTo(result.compositeScore(), TOLERANCE);
    }

    @Test
    void reportsTheNearestCriteriaLevelSoAScoreCanBeReadInWords() {
        stub(Map.of(
                "skills_coverage", score(3.4, 0.95),
                "experience_depth", score(2.0, 0.95),
                "role_relevance", score(2.0, 0.95),
                "education_fit", score(2.0, 0.95)),
                Map.of("location_compatible", 0.99));

        MatchResult result = service.match(posting, candidate, "generic");

        assertThat(result.dimensions().stream()
                        .filter(d -> d.key().equals("skills_coverage"))
                        .findFirst()
                        .orElseThrow()
                        .level())
                .isEqualTo("level-3");
    }

    @Test
    void weightsConfidenceByDimensionImportanceAndAutoAdvancesWhenDecisive() {
        stub(Map.of(
                "skills_coverage", score(4.0, 0.98),
                "experience_depth", score(3.0, 0.95),
                "role_relevance", score(3.0, 0.94),
                "education_fit", score(3.0, 0.92)),
                Map.of("location_compatible", 0.99));

        MatchResult result = service.match(posting, candidate, "generic");

        // .98*.35 + .95*.25 + .94*.25 + .92*.15 = 0.9535
        assertThat(result.confidence()).isCloseTo(0.9535d, TOLERANCE);
        assertThat(result.decision()).isEqualTo(ReviewDecision.AUTO_ADVANCE);
    }

    @Test
    void flagsForReviewWhenTheHeaviestDimensionIsUncertain() {
        stub(Map.of(
                "skills_coverage", score(2.0, 0.40),
                "experience_depth", score(3.0, 0.95),
                "role_relevance", score(3.0, 0.95),
                "education_fit", score(3.0, 0.95)),
                Map.of("location_compatible", 0.99));

        MatchResult result = service.match(posting, candidate, "generic");

        assertThat(result.confidence()).isLessThan(0.9d).isGreaterThan(0.5d);
        assertThat(result.decision()).isEqualTo(ReviewDecision.FLAG_FOR_REVIEW);
    }

    @Test
    void routesToAHumanWhenNoDimensionIsDecisive() {
        stub(Map.of(
                "skills_coverage", score(2.0, 0.30),
                "experience_depth", score(2.0, 0.30),
                "role_relevance", score(2.0, 0.30),
                "education_fit", score(2.0, 0.30)),
                Map.of("location_compatible", 0.99));

        assertThat(service.match(posting, candidate, "generic").decision())
                .isEqualTo(ReviewDecision.HUMAN_REVIEW);
    }

    /**
     * A gate is not a weighted term. A candidate who cannot take the job should not be auto-advanced
     * on the strength of an otherwise excellent score.
     */
    @Test
    void aFailedGateOverridesAnOtherwisePerfectScore() {
        stub(Map.of(
                "skills_coverage", score(4.0, 0.99),
                "experience_depth", score(4.0, 0.99),
                "role_relevance", score(4.0, 0.99),
                "education_fit", score(4.0, 0.99)),
                Map.of("location_compatible", 0.08));

        MatchResult result = service.match(posting, candidate, "generic");

        assertThat(result.compositeScore()).isCloseTo(1.0d, TOLERANCE);
        assertThat(result.decision()).isEqualTo(ReviewDecision.GATE_FAILED);
        assertThat(result.failedGates()).singleElement().satisfies(gate -> {
            assertThat(gate.key()).isEqualTo("location_compatible");
            assertThat(gate.passed()).isFalse();
        });
    }

    @Test
    void ranksCandidatesBestFirst() {
        when(jevClient.evaluate(any(), any()))
                .thenReturn(response(Map.of(
                        "skills_coverage", score(1.0, 0.9),
                        "experience_depth", score(1.0, 0.9),
                        "role_relevance", score(1.0, 0.9),
                        "education_fit", score(1.0, 0.9)),
                        Map.of("location_compatible", 0.9)))
                .thenReturn(response(Map.of(
                        "skills_coverage", score(4.0, 0.9),
                        "experience_depth", score(4.0, 0.9),
                        "role_relevance", score(4.0, 0.9),
                        "education_fit", score(4.0, 0.9)),
                        Map.of("location_compatible", 0.9)));

        List<MatchResult> ranked = service.rank(posting, List.of(candidate, candidate), "generic");

        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0).compositeScore()).isGreaterThan(ranked.get(1).compositeScore());
    }

    private void stub(Map<String, JevAnswer> scores, Map<String, Double> gates) {
        when(jevClient.evaluate(any(), any())).thenReturn(response(scores, gates));
    }

    private JevResponse response(Map<String, JevAnswer> scores, Map<String, Double> gates) {
        Map<String, JevAnswer> answers = new LinkedHashMap<>(scores);
        gates.forEach((key, value) -> answers.put(key, new JevAnswer.Noul("noul", value)));
        return new JevResponse("jev-1.13.0", answers, new JevResponse.Usage(300, 40));
    }

    /** A 5-level score answer, matching the shape of the generic rubric's dimensions. */
    private JevAnswer score(double value, double confidence) {
        Map<String, String> legend = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            legend.put(String.valueOf(i), "level-" + i);
        }
        return new JevAnswer.Score("score", value, legend, Map.of(), confidence);
    }
}
