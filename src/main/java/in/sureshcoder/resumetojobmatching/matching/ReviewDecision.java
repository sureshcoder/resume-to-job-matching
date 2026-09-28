package in.sureshcoder.resumetojobmatching.matching;

/**
 * What a human should do with a result, following TypeSafe's three-tier confidence guidance:
 * act automatically above 0.9, proceed with a check between 0.5 and 0.9, hand to a person below.
 */
public enum ReviewDecision {

    /** Confidence is high enough to act on the score without a person looking at it. */
    AUTO_ADVANCE,

    /** Usable, but a recruiter should confirm before it changes a candidate's status. */
    FLAG_FOR_REVIEW,

    /** The model was not decisive enough to be useful; a person should read the resume. */
    HUMAN_REVIEW,

    /** A mandatory requirement was not met, so the composite score is not the deciding factor. */
    GATE_FAILED
}
