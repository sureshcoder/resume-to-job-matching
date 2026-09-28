package in.sureshcoder.resumetojobmatching.jev;

import java.util.Map;

/**
 * A System One request: one state, evaluated against many questions in a single round trip.
 *
 * <p>Batching is the documented "speculative fan-out" pattern — the questions are evaluated in
 * parallel server-side, so asking twelve costs one call's latency rather than twelve.
 */
public record JevRequest(Object state, String model, Map<String, JevQuestion> questions) {}
