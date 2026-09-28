package in.sureshcoder.resumetojobmatching.matching;

import in.sureshcoder.resumetojobmatching.jev.JevResponse;
import java.util.List;

/**
 * The outcome of scoring one candidate against one posting.
 *
 * <p>The per-dimension breakdown is part of the contract, not debug output: a recruiter needs to
 * see which dimension carried or sank a composite before acting on it.
 */
public record MatchResult(
        /**
         * Who was scored. This is for our own UI only — StateBuilder strips identifying fields
         * before anything reaches TypeSafe, and that stays true.
         */
        String candidateName,
        String candidateHeadline,
        String jobTitle,
        String industry,
        String rubricId,
        boolean rubricFallback,
        double compositeScore,
        double confidence,
        ReviewDecision decision,
        List<DimensionScore> dimensions,
        List<GateResult> gates,
        JevResponse.Usage usage) {

    /** One weighted rubric dimension. */
    public record DimensionScore(
            String key,
            double rawScore,
            double normalisedScore,
            double weight,
            double contribution,
            double confidence,
            String level) {}

    /** One pass/fail requirement. {@code probability} is Jev's 0-1 truth value for the statement. */
    public record GateResult(String key, double probability, double minimum, boolean passed) {}

    public List<GateResult> failedGates() {
        return gates.stream().filter(gate -> !gate.passed()).toList();
    }
}
