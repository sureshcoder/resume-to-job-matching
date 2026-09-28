package in.sureshcoder.resumetojobmatching.jev;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.Map;

public record JevResponse(String model, Map<String, JevAnswer> answers, Usage usage) {

    /**
     * Token counts.
     *
     * <p>Jev sends these as {@code input_tokens}/{@code output_tokens}. They are read with
     * {@link JsonAlias} rather than {@code @JsonProperty} on purpose: {@code @JsonProperty} renames
     * the field for writing too, which would leak Jev's snake_case into our own API where every
     * other field is camelCase, and silently break clients reading {@code inputTokens}.
     */
    public record Usage(
            @JsonAlias("input_tokens") int inputTokens,
            @JsonAlias("output_tokens") int outputTokens) {}
}
