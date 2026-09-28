package in.sureshcoder.resumetojobmatching.jev;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

/**
 * One typed answer from Jev. The concrete shape is selected by the {@code type} discriminator.
 *
 * <p>Note the asymmetry: {@code choice} and {@code score} carry a probability distribution and a
 * {@code confidence}, but {@code noul} returns a bare probability with neither. {@link
 * #certainty()} normalises that difference so callers can gate on a single number.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = JevAnswer.Noul.class, name = "noul"),
    @JsonSubTypes.Type(value = JevAnswer.Choice.class, name = "choice"),
    @JsonSubTypes.Type(value = JevAnswer.Score.class, name = "score")
})
public sealed interface JevAnswer {

    String type();

    /**
     * How sure the model is, on 0–1, comparable across all three primitives.
     *
     * <p>For choice and score this is the confidence TypeSafe computes from the distribution. A
     * noul has no distribution to collapse, so its certainty is its distance from 0.5 — an answer
     * of 0.95 or 0.05 is decisive, one of 0.5 is a coin flip.
     */
    double certainty();

    record Noul(String type, double noul) implements JevAnswer {
        @Override
        public double certainty() {
            return Math.abs(noul - 0.5d) * 2d;
        }

        public boolean isTrue() {
            return noul >= 0.5d;
        }
    }

    record Choice(String type, String choice, Map<String, Double> probabilities, Double confidence)
            implements JevAnswer {
        @Override
        public double certainty() {
            return confidence == null ? 0d : confidence;
        }
    }

    record Score(
            String type,
            double score,
            Map<String, String> legend,
            Map<String, Double> probabilities,
            Double confidence)
            implements JevAnswer {

        @Override
        public double certainty() {
            return confidence == null ? 0d : confidence;
        }

        /**
         * The raw score rescaled to 0–1.
         *
         * <p>Jev returns a probability-weighted index into the criteria levels, so a 5-level rubric
         * yields 0–4. The legend tells us how many levels there were, which keeps this correct when
         * a rubric is edited without touching code.
         */
        public double normalised() {
            int levels = legend == null ? 0 : legend.size();
            if (levels < 2) {
                return 0d;
            }
            return Math.clamp(score / (levels - 1), 0d, 1d);
        }
    }
}
