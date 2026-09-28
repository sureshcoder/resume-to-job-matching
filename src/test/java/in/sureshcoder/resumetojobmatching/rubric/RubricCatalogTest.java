package in.sureshcoder.resumetojobmatching.rubric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.sureshcoder.resumetojobmatching.jev.JevQuestion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RubricCatalogTest {

    @Autowired
    RubricCatalog catalog;

    @Test
    void loadsEveryRubricFromYamlWithWeightsSummingToOne() {
        assertThat(catalog.availableRubrics()).contains("generic", "technology_information_and_media");
        catalog.availableRubrics().forEach(id -> assertThat(catalog.byId(id).rubric().totalWeight())
                .as("weights for rubric %s", id)
                .isCloseTo(1.0d, org.assertj.core.data.Offset.offset(0.001d)));
    }

    /**
     * Question keys are the join between the rubric and the Jev response, so relaxed configuration
     * binding must not rewrite them. If Spring lower-camelised {@code skills_coverage}, every
     * dimension lookup would silently miss.
     */
    @Test
    void preservesSnakeCaseQuestionKeysThroughConfigurationBinding() {
        assertThat(catalog.byId("generic").rubric().getQuestions())
                .containsKeys("skills_coverage", "experience_depth", "role_relevance", "education_fit");
    }

    @Test
    void separatesScoredDimensionsFromGates() {
        Rubric generic = catalog.byId("generic").rubric();
        assertThat(generic.scoredQuestions()).hasSize(4);
        assertThat(generic.gateQuestions()).containsOnlyKeys("location_compatible");
    }

    @Test
    void buildsWireQuestionsWithTheCriteriaShapeEachTypeRequires() {
        Map<String, JevQuestion> wire = catalog.byId("generic").rubric().toJevQuestions();

        assertThat(wire.get("skills_coverage").type()).isEqualTo("score");
        assertThat(wire.get("skills_coverage").criteria()).isInstanceOf(List.class);
        assertThat(wire.get("location_compatible").type()).isEqualTo("noul");
        assertThat(wire.get("location_compatible").criteria()).isInstanceOf(Map.class);
    }

    @Test
    void mapsIndustryAliasesAndDisplayNamesOntoCanonicalRubricKeys() {
        // The faker accepts the alias "technology"...
        assertThat(catalog.forIndustry("technology").id()).isEqualTo("technology_information_and_media");
        // ...but a built posting reports the industry as a display name.
        assertThat(catalog.forIndustry("Technology, Information and Media").id())
                .isEqualTo("technology_information_and_media");
        assertThat(catalog.forIndustry("healthcare").id()).isEqualTo("hospitals_and_health_care");
    }

    @Test
    void fallsBackToTheGenericRubricForAnIndustryWithNoRubricOfItsOwn() {
        RubricCatalog.ResolvedRubric resolved = catalog.forIndustry("farming_ranching_forestry");
        assertThat(resolved.id()).isEqualTo("generic");
        assertThat(resolved.fallback()).isTrue();
    }

    @Test
    void rejectsAnUnknownRubricId() {
        assertThatThrownBy(() -> catalog.byId("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown rubric");
    }
}
