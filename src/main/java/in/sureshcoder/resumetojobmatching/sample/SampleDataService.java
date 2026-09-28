package in.sureshcoder.resumetojobmatching.sample;

import in.sureshcoder.datafaker.candidate.faker.CandidateFaker;
import in.sureshcoder.datafaker.candidate.model.Candidate;
import in.sureshcoder.datafaker.jobposting.faker.JobFaker;
import in.sureshcoder.datafaker.jobposting.model.JobPosting;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

/**
 * Synthetic postings and candidates from data-faker-custom-provider.
 *
 * <p>Both fakers draw from per-industry pools, so a candidate and a posting built for the same
 * industry share a skills vocabulary while ones built for different industries do not. That gives
 * a cheap approximate ground truth for sanity-checking the matcher without hand-labelling: same
 * industry should score well above cross-industry, and if it does not, the rubric is wrong.
 *
 * <p>Passing a seed makes a run reproducible, which is what lets a rubric change be compared
 * against the previous rubric on identical data.
 */
@Service
public class SampleDataService {

    public JobPosting jobPosting(String industry, Long seed) {
        JobFaker faker = seed == null ? new JobFaker() : new JobFaker(new Random(seed));
        return industry == null || industry.isBlank()
                ? faker.jobPosting().build()
                : faker.jobPosting().buildForIndustry(industry);
    }

    public Candidate candidate(String industry, Long seed) {
        CandidateFaker faker = seed == null ? new CandidateFaker() : new CandidateFaker(new Random(seed));
        return industry == null || industry.isBlank()
                ? faker.candidate().build()
                : faker.candidate().buildForIndustry(industry);
    }

    /**
     * A pool of candidates for one posting.
     *
     * <p>The seed is offset per candidate; reusing one seed across a loop would return the same
     * person every time, since the faker is deterministic for a given seed.
     */
    public List<Candidate> candidates(String industry, int count, Long seed) {
        return IntStream.range(0, count)
                .mapToObj(i -> candidate(industry, seed == null ? null : seed + i))
                .toList();
    }

    public List<String> availableIndustries() {
        return new JobFaker().jobPosting().availableIndustries();
    }
}
