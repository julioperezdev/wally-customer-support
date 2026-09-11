package com.wally.customersupport.conversation.application.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Detects only explicit reactivation commands. A generic message must never
 * override a previous opt-out.
 */
@Component
public class OptInDetector {

    private static final Set<String> EXACT_TERMS = Set.of(
            "alta", "reanudar", "start");

    public boolean isOptIn(String message) {
        String normalized = normalize(message);
        return EXACT_TERMS.contains(normalized);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9áéíóúüñ]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
