package com.wally.customersupport.conversation.application.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class OptOutDetector {

    private static final Set<String> EXACT_TERMS = Set.of(
            "baja", "stop", "unsubscribe", "cancelar suscripcion", "cancelar suscripción");
    private static final Set<String> PHRASES = Set.of(
            "no me contacten",
            "no me contactes",
            "no quiero recibir mensajes",
            "no quiero recibir mas mensajes",
            "dejen de escribirme",
            "deja de escribirme",
            "no me escriban",
            "no me escribas");

    public boolean isOptOut(String message) {
        String normalized = normalize(message);
        return EXACT_TERMS.contains(normalized)
                || PHRASES.stream().anyMatch(normalized::contains);
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
