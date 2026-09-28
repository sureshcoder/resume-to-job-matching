package in.sureshcoder.resumetojobmatching.rubric;

import in.sureshcoder.resumetojobmatching.jev.JevQuestion;
import java.util.List;
import java.util.Map;

/**
 * One rubric dimension as authored in YAML.
 *
 * <p>This is deliberately a flatter shape than {@link JevQuestion}: config binding wants plain
 * typed properties, while the wire format overloads a single {@code criteria} field whose shape
 * depends on the question type. {@link #toJevQuestion()} bridges the two.
 *
 * <p>A {@code score} question is a weighted contributor to the composite. A {@code noul} question
 * is a gate — a hard requirement that is checked but never averaged in, because blending a
 * pass/fail requirement into a weighted mean lets a strong candidate score around a missing
 * mandatory qualification.
 */
public class RubricQuestion {

    private String type = "score";
    private String instructions;
    private List<String> levels;
    private Map<String, String> options;
    private double weight;
    private double minimum = 0.5d;

    public JevQuestion toJevQuestion() {
        return switch (type) {
            case "score" -> JevQuestion.score(instructions, levels);
            case "noul" -> JevQuestion.noul(instructions, options);
            case "choice" -> JevQuestion.choice(instructions, options);
            default -> throw new IllegalStateException("unknown question type: " + type);
        };
    }

    public boolean isScored() {
        return "score".equals(type);
    }

    public boolean isGate() {
        return "noul".equals(type);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public List<String> getLevels() {
        return levels;
    }

    public void setLevels(List<String> levels) {
        this.levels = levels;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = options;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public double getMinimum() {
        return minimum;
    }

    public void setMinimum(double minimum) {
        this.minimum = minimum;
    }
}
