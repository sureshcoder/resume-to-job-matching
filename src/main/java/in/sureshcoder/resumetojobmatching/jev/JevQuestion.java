package in.sureshcoder.resumetojobmatching.jev;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * A single typed question as it goes over the wire.
 *
 * <p>{@code criteria} is deliberately loosely typed because its shape depends on {@code type}: an
 * ordered {@code List<String>} of levels for {@code score}, and a {@code Map<String, String>} of
 * option to description for {@code choice} and {@code noul}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JevQuestion(String type, String instructions, Object criteria) {

    public static JevQuestion score(String instructions, List<String> levels) {
        if (levels == null || levels.size() < 2) {
            throw new IllegalArgumentException("a score question needs at least 2 criteria levels");
        }
        return new JevQuestion("score", instructions, levels);
    }

    public static JevQuestion choice(String instructions, Map<String, String> options) {
        if (options == null || options.size() < 2) {
            throw new IllegalArgumentException("a choice question needs at least 2 options");
        }
        return new JevQuestion("choice", instructions, options);
    }

    public static JevQuestion noul(String instructions, Map<String, String> criteria) {
        return new JevQuestion("noul", instructions, criteria);
    }
}
