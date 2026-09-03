package com.wally.customersupport.application.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import com.wally.customersupport.application.port.out.ConversationIntentClassifier;
import com.wally.customersupport.domain.model.ConversationIntent;
import com.wally.customersupport.domain.model.ConversationIntentDecision;
import com.wally.customersupport.domain.model.CatalogQuery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Test/local double that preserves the same structured contract as the LLM classifier. */
@Component
@ConditionalOnProperty(name = "wcs.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockConversationIntentClassifier implements ConversationIntentClassifier {

    private static final Pattern GREETING = Pattern.compile("\\b(hola|buenas|buen dia|buenas tardes|buenas noches)\\b");
    private static final Pattern HOURS = Pattern.compile("\\b(horario|horarios|abierto|abren|cierran)\\b");
    private static final Pattern HANDOFF = Pattern.compile("\\b(persona|agente|humano|asesor)\\b");

    @Override
    public ConversationIntentDecision classify(String message) {
        String normalized = normalize(message);
        if (normalized.isBlank()) {
            return ConversationIntentDecision.unknown();
        }
        if (GREETING.matcher(normalized).find()) {
            return new ConversationIntentDecision(ConversationIntent.GREETING, 0.99, null, null);
        }
        if (HANDOFF.matcher(normalized).find()) {
            return new ConversationIntentDecision(ConversationIntent.HUMAN_HANDOFF, 0.99, null, null);
        }
        if (HOURS.matcher(normalized).find()) {
            return new ConversationIntentDecision(ConversationIntent.BUSINESS_HOURS, 0.98, null, null);
        }

        String policyKey = policyKey(normalized);
        if (policyKey != null) {
            return new ConversationIntentDecision(ConversationIntent.POLICY_QUERY, 0.96, null, policyKey);
        }

        Optional<CatalogQuery> catalogQuery = CatalogQueryParser.parse(message);
        if (catalogQuery.isPresent()) {
            return new ConversationIntentDecision(ConversationIntent.CATALOG_SEARCH, 0.95,
                    catalogQuery.get(), null);
        }
        return new ConversationIntentDecision(ConversationIntent.GENERAL_SUPPORT, 0.80, null, null);
    }

    private static String policyKey(String normalized) {
        if (containsAny(normalized, "envio", "envios", "entrega", "despacho")) {
            return "shipping";
        }
        if (containsAny(normalized, "pago", "pagos", "tarjeta", "transferencia")) {
            return "payments";
        }
        if (containsAny(normalized, "cambio", "cambios")) {
            return "changes";
        }
        if (containsAny(normalized, "devolucion", "devoluciones", "reembolso")) {
            return "returns";
        }
        return null;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
