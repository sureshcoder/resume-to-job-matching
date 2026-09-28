package in.sureshcoder.resumetojobmatching.matching;

import static org.assertj.core.api.Assertions.assertThat;

import in.sureshcoder.datafaker.candidate.faker.CandidateFaker;
import in.sureshcoder.datafaker.candidate.model.Candidate;
import in.sureshcoder.datafaker.jobposting.faker.JobFaker;
import in.sureshcoder.datafaker.jobposting.model.JobPosting;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Hits the real Jev API. Skipped unless {@code TYPESAFE_API_KEY} is exported, so the rest of the
 * suite stays runnable offline and in CI without a secret.
 *
 * <p>The cross-industry assertion is the useful one: because the faker draws postings and
 * candidates from per-industry pools, a technology candidate should beat a farming candidate on a
 * technology posting. If that ordering ever inverts, the rubric is measuring the wrong thing.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
class JevLiveMatchingIT {

    @Autowired
    MatchingService matchingService;

    @Test
    void scoresAWellMatchedCandidateAboveAnUnrelatedOne() {
        JobPosting posting = new JobFaker(new Random(11)).jobPosting().buildForIndustry("technology");
        Candidate matching = new CandidateFaker(new Random(11)).candidate().buildForIndustry("technology");
        Candidate unrelated =
                new CandidateFaker(new Random(11)).candidate().buildForIndustry("farming_ranching_forestry");

        MatchResult good = matchingService.match(posting, matching);
        MatchResult bad = matchingService.match(posting, unrelated);

        assertThat(good.rubricId()).isEqualTo("technology_information_and_media");
        assertThat(good.dimensions()).isNotEmpty();
        assertThat(good.usage().inputTokens()).isPositive();
        assertThat(good.compositeScore()).isGreaterThan(bad.compositeScore());
    }
}
