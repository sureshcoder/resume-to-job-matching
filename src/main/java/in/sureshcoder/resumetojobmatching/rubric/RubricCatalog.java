package in.sureshcoder.resumetojobmatching.rubric;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The hand-authored rubrics, keyed by the faker's industry key.
 *
 * <p>Rubrics live in {@code rubrics.yaml} rather than in Java so that adding a role is a config
 * edit, matching the YAML-driven approach of the data-faker-custom-provider library that produces
 * the postings and candidates.
 */
@Component
@ConfigurationProperties(prefix = "matching")
public class RubricCatalog {

    private static final Logger log = LoggerFactory.getLogger(RubricCatalog.class);
    private static final double WEIGHT_TOLERANCE = 0.001d;

    /**
     * Industry aliases the faker accepts, mapped to their canonical keys, so a rubric authored for
     * {@code technology_information_and_media} is also found by a posting built for
     * {@code technology}.
     */
    private static final Map<String, String> ALIASES = Map.of(
            "technology", "technology_information_and_media",
            "healthcare", "hospitals_and_health_care",
            "finance", "financial_services",
            "logistics", "transportation_logistics_supply_chain_and_storage",
            "staffing", "administrative_and_support_services",
            "consulting", "professional_services",
            "media", "entertainment_providers");

    private String defaultRubric = "generic";
    private Map<String, Rubric> rubrics = Map.of();

    /**
     * Fails fast on a miswritten rubric. A weight set that does not sum to 1.0 still produces a
     * number, just a silently wrong one, so it is worth catching at startup rather than in a score.
     */
    @PostConstruct
    void validate() {
        if (rubrics.isEmpty()) {
            throw new IllegalStateException("no rubrics configured under 'matching.rubrics'");
        }
        if (!rubrics.containsKey(defaultRubric)) {
            throw new IllegalStateException(
                    "matching.default-rubric '%s' is not one of %s".formatted(defaultRubric, rubrics.keySet()));
        }
        rubrics.forEach((id, rubric) -> {
            if (rubric.scoredQuestions().isEmpty()) {
                throw new IllegalStateException("rubric '%s' has no scored dimensions".formatted(id));
            }
            double total = rubric.totalWeight();
            if (Math.abs(total - 1.0d) > WEIGHT_TOLERANCE) {
                throw new IllegalStateException(
                        "rubric '%s' weights sum to %.3f, expected 1.0".formatted(id, total));
            }
            rubric.getQuestions().forEach((key, question) -> {
                if (question.getInstructions() == null || question.getInstructions().isBlank()) {
                    throw new IllegalStateException(
                            "rubric '%s' question '%s' has no instructions".formatted(id, key));
                }
                if (question.isScored() && (question.getLevels() == null || question.getLevels().size() < 2)) {
                    throw new IllegalStateException(
                            "rubric '%s' question '%s' needs at least 2 levels".formatted(id, key));
                }
            });
        });
        log.info("Loaded {} rubric(s): {}", rubrics.size(), rubrics.keySet());
    }

    /** Resolves an industry to its rubric, falling back to the default for unmapped industries. */
    public ResolvedRubric forIndustry(String industry) {
        String key = canonicalKey(industry);
        Rubric rubric = rubrics.get(key);
        if (rubric != null) {
            return new ResolvedRubric(key, rubric, false);
        }
        return new ResolvedRubric(defaultRubric, rubrics.get(defaultRubric), true);
    }

    public ResolvedRubric byId(String rubricId) {
        Rubric rubric = rubrics.get(rubricId);
        if (rubric == null) {
            throw new IllegalArgumentException(
                    "unknown rubric '%s'; configured rubrics are %s".formatted(rubricId, rubrics.keySet()));
        }
        return new ResolvedRubric(rubricId, rubric, false);
    }

    public List<String> availableRubrics() {
        return List.copyOf(rubrics.keySet());
    }

    /**
     * Normalises an industry into a rubric key.
     *
     * <p>The faker reports {@code industry} as a display name such as {@code "Technology,
     * Information and Media"}, while its YAML keys are snake_case, so this lower-cases and
     * replaces every run of non-alphanumeric characters before applying the alias table.
     */
    static String canonicalKey(String industry) {
        if (industry == null || industry.isBlank()) {
            return "";
        }
        String normalised = industry
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
        return ALIASES.getOrDefault(normalised, normalised);
    }

    public record ResolvedRubric(String id, Rubric rubric, boolean fallback) {}

    public String getDefaultRubric() {
        return defaultRubric;
    }

    public void setDefaultRubric(String defaultRubric) {
        this.defaultRubric = defaultRubric;
    }

    public Map<String, Rubric> getRubrics() {
        return rubrics;
    }

    public void setRubrics(Map<String, Rubric> rubrics) {
        this.rubrics = rubrics;
    }
}
