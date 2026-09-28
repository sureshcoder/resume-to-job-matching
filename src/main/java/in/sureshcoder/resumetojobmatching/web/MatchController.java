package in.sureshcoder.resumetojobmatching.web;

import in.sureshcoder.datafaker.candidate.model.Candidate;
import in.sureshcoder.datafaker.jobposting.model.JobPosting;
import in.sureshcoder.resumetojobmatching.matching.MatchResult;
import in.sureshcoder.resumetojobmatching.matching.MatchingService;
import in.sureshcoder.resumetojobmatching.rubric.RubricCatalog;
import in.sureshcoder.resumetojobmatching.sample.SampleDataService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MatchController {

    private final MatchingService matchingService;
    private final SampleDataService sampleData;
    private final RubricCatalog catalog;

    public MatchController(
            MatchingService matchingService, SampleDataService sampleData, RubricCatalog catalog) {
        this.matchingService = matchingService;
        this.sampleData = sampleData;
        this.catalog = catalog;
    }

    /** Scores a caller-supplied posting and candidate. */
    @PostMapping("/match")
    public MatchResult match(@Valid @RequestBody MatchRequest request) {
        return matchingService.match(request.jobPosting(), request.candidate(), request.rubricId());
    }

    /**
     * Generates a posting and a pool of candidates, then ranks them.
     *
     * <p>Set {@code candidateIndustry} to something other than {@code industry} to see the matcher
     * separate genuinely unsuitable candidates from plausible ones.
     */
    @PostMapping("/demo/rank")
    public RankResponse demoRank(
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String candidateIndustry,
            @RequestParam(defaultValue = "5") @Min(1) @Max(25) int candidates,
            @RequestParam(required = false) Long seed,
            @RequestParam(required = false) String rubricId) {

        JobPosting posting = sampleData.jobPosting(industry, seed);
        List<Candidate> pool =
                sampleData.candidates(candidateIndustry == null ? industry : candidateIndustry, candidates, seed);
        return new RankResponse(posting, matchingService.rank(posting, pool, rubricId));
    }

    /** A generated posting and candidate, so callers can see the shape the matcher expects. */
    @GetMapping("/demo/sample")
    public MatchRequest sample(
            @RequestParam(required = false) String industry, @RequestParam(required = false) Long seed) {
        return new MatchRequest(sampleData.jobPosting(industry, seed), sampleData.candidate(industry, seed), null);
    }

    /**
     * Full rubric definitions, including criteria levels and weights.
     *
     * <p>The weights are returned because the client re-applies them locally: re-weighting a result
     * needs no new Jev call, since the per-dimension scores are already normalised.
     */
    @GetMapping("/rubrics")
    public List<RubricView> rubrics() {
        return catalog.availableRubrics().stream()
                .map(id -> RubricView.of(
                        id, catalog.byId(id).rubric(), id.equals(catalog.getDefaultRubric())))
                .toList();
    }

    @GetMapping("/industries")
    public List<String> industries() {
        return sampleData.availableIndustries();
    }

    public record MatchRequest(
            @NotNull JobPosting jobPosting, @NotNull Candidate candidate, String rubricId) {}

    public record RankResponse(JobPosting jobPosting, List<MatchResult> results) {}
}
