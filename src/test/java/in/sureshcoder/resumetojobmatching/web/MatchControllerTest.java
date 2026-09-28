package in.sureshcoder.resumetojobmatching.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import in.sureshcoder.resumetojobmatching.jev.JevAnswer;
import in.sureshcoder.resumetojobmatching.jev.JevClient;
import in.sureshcoder.resumetojobmatching.jev.JevResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;

/** Jev is mocked out: these endpoints must work without an API key. */
@SpringBootTest
class MatchControllerTest {

    @MockitoBean
    JevClient jevClient;

    @Autowired
    WebApplicationContext context;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void returnsFullRubricDefinitionsIncludingCriteriaLevels() throws Exception {
        mockMvc().perform(get("/api/v1/rubrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("generic"))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[0].displayName").value("Generic role"))
                // The levels are what make a returned score readable in the UI.
                .andExpect(jsonPath("$[0].questions[0].key").value("skills_coverage"))
                .andExpect(jsonPath("$[0].questions[0].type").value("score"))
                .andExpect(jsonPath("$[0].questions[0].levels").isArray())
                .andExpect(jsonPath("$[0].questions[0].weight").value(0.35))
                // Gates carry options rather than levels.
                .andExpect(jsonPath("$[0].questions[4].key").value("location_compatible"))
                .andExpect(jsonPath("$[0].questions[4].type").value("noul"));
    }

    /**
     * The UI reads `usage.inputTokens`. Jev sends `input_tokens`, and an `@JsonProperty` used to
     * rename the field for writing as well as reading, so our own API leaked snake_case and the
     * client read undefined. This pins the shape at the boundary that actually broke.
     */
    @Test
    void returnsUsageInCamelCaseNotJevsSnakeCase() throws Exception {
        when(jevClient.evaluate(any(), any())).thenReturn(stubResponse());

        String sample = mockMvc()
                .perform(get("/api/v1/demo/sample?industry=technology&seed=42"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc().perform(post("/api/v1/match").contentType(MediaType.APPLICATION_JSON).content(sample))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usage.inputTokens").value(312))
                .andExpect(jsonPath("$.usage.outputTokens").value(48))
                .andExpect(jsonPath("$.usage.input_tokens").doesNotExist())
                // The rest of the payload the UI depends on.
                .andExpect(jsonPath("$.candidateName").isNotEmpty())
                .andExpect(jsonPath("$.compositeScore").isNumber())
                .andExpect(jsonPath("$.decision").isNotEmpty())
                .andExpect(jsonPath("$.dimensions[0].normalisedScore").isNumber())
                .andExpect(jsonPath("$.gates[0].passed").isBoolean());
    }

    private JevResponse stubResponse() {
        Map<String, String> legend = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            legend.put(String.valueOf(i), "level-" + i);
        }
        Map<String, JevAnswer> answers = new LinkedHashMap<>();
        List.of("skills_coverage", "technical_depth", "role_relevance", "system_scale", "education_fit")
                .forEach(key -> answers.put(key, new JevAnswer.Score("score", 3.0, legend, Map.of(), 0.95)));
        answers.put("location_compatible", new JevAnswer.Noul("noul", 0.95));
        return new JevResponse("jev-1.13.0", answers, new JevResponse.Usage(312, 48));
    }

    @Test
    void listsEveryIndustryTheFakerSupports() throws Exception {
        mockMvc().perform(get("/api/v1/industries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20));
    }

    @Test
    void generatesASamplePairWithoutCallingJev() throws Exception {
        mockMvc().perform(get("/api/v1/demo/sample?industry=technology&seed=42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobPosting.title").isNotEmpty())
                .andExpect(jsonPath("$.jobPosting.skills").isArray())
                .andExpect(jsonPath("$.candidate.skills").isArray());
    }
}
