package in.sureshcoder.resumetojobmatching.matching;

import static org.assertj.core.api.Assertions.assertThat;

import in.sureshcoder.datafaker.candidate.faker.CandidateFaker;
import in.sureshcoder.datafaker.candidate.model.Candidate;
import in.sureshcoder.datafaker.jobposting.faker.JobFaker;
import in.sureshcoder.datafaker.jobposting.model.JobPosting;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StateBuilderTest {

    private final StateBuilder stateBuilder = new StateBuilder();
    private JobPosting posting;
    private Candidate candidate;

    @BeforeEach
    void setUp() {
        posting = new JobFaker(new Random(7)).jobPosting().buildForIndustry("technology");
        candidate = new CandidateFaker(new Random(7)).candidate().buildForIndustry("technology");
    }

    /**
     * Identifying fields must not leave for a third-party API. They carry no matching signal, and
     * sending them would put real candidate PII in a vendor's request logs.
     */
    @Test
    void omitsIdentifyingFieldsFromTheStateSentToJev() {
        String serialised = stateBuilder.build(posting, candidate).toString();

        assertThat(serialised)
                .doesNotContain(candidate.firstName())
                .doesNotContain(candidate.lastName())
                .doesNotContain(candidate.email())
                .doesNotContain(candidate.mobileNumber())
                .doesNotContain(candidate.address().addressLine1());
    }

    @Test
    void keepsCityAndStateBecauseLocationIsAMatchingSignal() {
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) stateBuilder.build(posting, candidate).get("candidate");

        assertThat((String) state.get("location"))
                .contains(candidate.address().city())
                .contains(candidate.address().state());
    }

    @Test
    void putsBothSidesInOneStateSoRubricsCanCompareThem() {
        Map<String, Object> state = stateBuilder.build(posting, candidate);

        assertThat(state).containsOnlyKeys("job_posting", "candidate");

        @SuppressWarnings("unchecked")
        Map<String, Object> job = (Map<String, Object>) state.get("job_posting");
        assertThat(job).containsEntry("title", posting.title()).containsEntry("required_skills", posting.skills());
    }

    /** Jev reads language rather than doing arithmetic, so tenure is computed before it is sent. */
    @Test
    void precomputesTotalYearsOfExperience() {
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) stateBuilder.build(posting, candidate).get("candidate");

        assertThat((Double) state.get("total_years_experience")).isGreaterThan(0d);
    }

    @Test
    void marksTheCurrentJobAsOngoingRatherThanEmitting_null() {
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) stateBuilder.build(posting, candidate).get("candidate");
        @SuppressWarnings("unchecked")
        var jobs = (java.util.List<Map<String, Object>>) state.get("job_history");

        assertThat(jobs.getLast()).containsEntry("end_date", "present").containsEntry("is_current", true);
    }
}
