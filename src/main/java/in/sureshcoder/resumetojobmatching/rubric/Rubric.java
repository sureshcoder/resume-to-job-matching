package in.sureshcoder.resumetojobmatching.rubric;

import in.sureshcoder.resumetojobmatching.jev.JevQuestion;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** A named set of scored dimensions plus gate questions, with the weights applied in our code. */
public class Rubric {

    private String displayName;
    private Map<String, RubricQuestion> questions = new LinkedHashMap<>();

    /** The whole rubric as a single batched Jev call — one round trip, evaluated in parallel. */
    public Map<String, JevQuestion> toJevQuestions() {
        Map<String, JevQuestion> wire = new LinkedHashMap<>();
        questions.forEach((key, question) -> wire.put(key, question.toJevQuestion()));
        return wire;
    }

    public Map<String, RubricQuestion> scoredQuestions() {
        return questions.entrySet().stream()
                .filter(e -> e.getValue().isScored())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    public Map<String, RubricQuestion> gateQuestions() {
        return questions.entrySet().stream()
                .filter(e -> e.getValue().isGate())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    public double totalWeight() {
        return scoredQuestions().values().stream()
                .mapToDouble(RubricQuestion::getWeight)
                .sum();
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Map<String, RubricQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(Map<String, RubricQuestion> questions) {
        this.questions = questions;
    }
}
