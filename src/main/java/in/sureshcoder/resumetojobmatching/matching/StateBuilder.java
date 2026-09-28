package in.sureshcoder.resumetojobmatching.matching;

import in.sureshcoder.datafaker.candidate.model.Candidate;
import in.sureshcoder.datafaker.candidate.model.Certification;
import in.sureshcoder.datafaker.candidate.model.EducationHistory;
import in.sureshcoder.datafaker.candidate.model.JobHistory;
import in.sureshcoder.datafaker.jobposting.model.JobPosting;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code state} object sent to Jev.
 *
 * <p>Two things are deliberate here.
 *
 * <p>First, identifying fields are dropped. Name, email, phone and street address say nothing about
 * whether someone can do a job, so there is no reason to send them to a third-party API. City and
 * state stay because location compatibility is a real matching signal.
 *
 * <p>Second, the posting and the candidate go into a single state together. Jev can only answer
 * questions about what it is given, so a rubric that asks "does this candidate cover the skills
 * this posting asks for" needs both sides in front of it. Both fit comfortably: a candidate is
 * roughly 1-2k tokens against a 32k state budget.
 */
@Component
public class StateBuilder {

    public Map<String, Object> build(JobPosting posting, Candidate candidate) {
        return Map.of("job_posting", posting(posting), "candidate", candidate(candidate));
    }

    private Map<String, Object> posting(JobPosting posting) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("industry", posting.industry());
        map.put("title", posting.title());
        map.put("description", posting.description());
        map.put("required_skills", posting.skills());
        map.put("employment_type", posting.employmentType());
        map.put("location", posting.jobLocation());
        if (posting.baseSalary() != null) {
            map.put(
                    "salary_range",
                    "%s %d-%d per %s"
                            .formatted(
                                    posting.baseSalary().currency(),
                                    posting.baseSalary().minValue(),
                                    posting.baseSalary().maxValue(),
                                    posting.baseSalary().unitText()));
        }
        return map;
    }

    private Map<String, Object> candidate(Candidate candidate) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("professional_summary", candidate.professionalSummary());
        map.put("skills", candidate.skills());
        map.put("total_years_experience", totalYearsExperience(candidate.jobHistory()));
        map.put("location", location(candidate));
        map.put("education", candidate.educationHistory().stream().map(this::education).toList());
        map.put("job_history", candidate.jobHistory().stream().map(this::job).toList());
        map.put("certifications", candidate.certifications().stream().map(this::certification).toList());
        return map;
    }

    private String location(Candidate candidate) {
        var address = candidate.address();
        return address == null ? "unknown" : "%s, %s".formatted(address.city(), address.state());
    }

    private Map<String, Object> education(EducationHistory education) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("degree_level", education.degreeLevel());
        map.put("course", education.courseName());
        map.put("specialization", education.specialization());
        map.put("institution", education.institutionName());
        map.put("completed", String.valueOf(education.endDate()));
        map.put("score", "%s %.1f".formatted(education.scoreType(), education.scoreValue()));
        return map;
    }

    private Map<String, Object> job(JobHistory job) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("designation", job.designation());
        map.put("role", job.role());
        map.put("company", job.companyName());
        map.put("start_date", String.valueOf(job.startDate()));
        map.put("end_date", job.isCurrent() ? "present" : String.valueOf(job.endDate()));
        map.put("is_current", job.isCurrent());
        map.put("duration_months", monthsBetween(job.startDate(), job.endDate()));
        map.put("responsibilities", job.responsibilities());
        map.put("skills", job.skills());
        return map;
    }

    private Map<String, Object> certification(Certification certification) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", certification.name());
        map.put("issuing_organization", certification.issuingOrganization());
        map.put("issued", String.valueOf(certification.issuedDate()));
        return map;
    }

    /**
     * Summed months of employment, rounded to one decimal.
     *
     * <p>Jev reads natural language rather than doing arithmetic, so pre-computing tenure gives the
     * rubric a fact to judge instead of a date range to work out.
     */
    private double totalYearsExperience(List<JobHistory> history) {
        long months = history.stream()
                .mapToLong(job -> monthsBetween(job.startDate(), job.endDate()))
                .sum();
        return Math.round(months / 12.0d * 10d) / 10.0d;
    }

    private long monthsBetween(LocalDate start, LocalDate end) {
        if (start == null) {
            return 0L;
        }
        return Math.max(0L, ChronoUnit.MONTHS.between(start, end == null ? LocalDate.now() : end));
    }
}
