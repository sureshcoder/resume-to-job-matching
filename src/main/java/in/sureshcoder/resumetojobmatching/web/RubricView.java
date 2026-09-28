package in.sureshcoder.resumetojobmatching.web;

import in.sureshcoder.resumetojobmatching.rubric.Rubric;
import in.sureshcoder.resumetojobmatching.rubric.RubricQuestion;
import java.util.List;
import java.util.Map;

/**
 * A rubric as the UI needs to see it: the criteria levels are what make a score readable, so they
 * travel with the definition rather than being looked up per question.
 */
public record RubricView(
        String id, String displayName, boolean isDefault, List<QuestionView> questions) {

    public record QuestionView(
            String key,
            String type,
            String instructions,
            List<String> levels,
            Map<String, String> options,
            double weight,
            double minimum) {}

    public static RubricView of(String id, Rubric rubric, boolean isDefault) {
        List<QuestionView> questions = rubric.getQuestions().entrySet().stream()
                .map(entry -> toQuestionView(entry.getKey(), entry.getValue()))
                .toList();
        return new RubricView(id, rubric.getDisplayName(), isDefault, questions);
    }

    private static QuestionView toQuestionView(String key, RubricQuestion question) {
        return new QuestionView(
                key,
                question.getType(),
                question.getInstructions(),
                question.getLevels(),
                question.getOptions(),
                question.getWeight(),
                question.getMinimum());
    }
}
