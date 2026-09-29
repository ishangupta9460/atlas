package com.atlas.backend.ai;

/** Infrastructure proof shape only; not a domain proposal or onboarding contract. */
public record AiQuestion(String type, String question, String reason) {
    @Override public String toString() { return "AiQuestion[content omitted]"; }
}
