package com.wally.customersupport.conversation.domain.model;

import java.util.Objects;

/** Output of a response policy; it contains presentation, not new facts. */
public record ResponseHumanizationResult(
        String text,
        String policyId,
        String policyVersion,
        Outcome outcome,
        String fallbackReason) {

    public enum Outcome {
        APPLIED,
        FALLBACK
    }

    public ResponseHumanizationResult {
        text = required(text, "text");
        policyId = required(policyId, "policyId");
        policyVersion = required(policyVersion, "policyVersion");
        outcome = Objects.requireNonNull(outcome, "outcome");
        fallbackReason = normalize(fallbackReason);
        if (outcome == Outcome.FALLBACK && fallbackReason == null) {
            throw new IllegalArgumentException("fallback outcome requires fallbackReason");
        }
        if (outcome == Outcome.APPLIED && fallbackReason != null) {
            throw new IllegalArgumentException("applied outcome must not have fallbackReason");
        }
    }

    public static ResponseHumanizationResult applied(
            String text,
            String policyId,
            String policyVersion) {
        return new ResponseHumanizationResult(text, policyId, policyVersion, Outcome.APPLIED, null);
    }

    public static ResponseHumanizationResult fallback(
            String text,
            String policyId,
            String policyVersion,
            String reason) {
        return new ResponseHumanizationResult(text, policyId, policyVersion, Outcome.FALLBACK, reason);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
