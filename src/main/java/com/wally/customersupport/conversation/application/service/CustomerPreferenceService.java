package com.wally.customersupport.conversation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.out.CustomerPreferenceStore;
import com.wally.customersupport.conversation.domain.model.CustomerPreference;
import com.wally.customersupport.conversation.domain.model.PreferenceOrigin;
import com.wally.customersupport.conversation.domain.model.PreferenceScope;
import com.wally.customersupport.shared.infrastructure.config.ConversationPreferenceProperties;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Validates the narrow preference contract before it reaches persistence.
 * Automatic extraction is intentionally not part of this service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerPreferenceService {

    public static final String PREFERRED_COLOR = "preferred_color";

    private static final List<String> ALLOWED_COLORS = List.of(
            "negro", "blanco", "gris", "azul", "rojo", "verde", "amarillo", "rosa", "violeta");

    private final CustomerPreferenceStore store;
    private final ConversationPreferenceProperties properties;
    private final Clock clock;

    public List<CustomerPreference> findForContext(String actorId, UUID conversationId) {
        if (!properties.enabled() || actorId == null || actorId.isBlank()) {
            return List.of();
        }
        return store.findActive(actorId.strip(), conversationId, clock.instant()).stream()
                .limit(properties.effectiveMaxPreferences())
                .toList();
    }

    public Optional<CustomerPreference> recordExplicitColor(
            String actorId,
            String color,
            Instant updatedAt) {
        if (!properties.enabled() || actorId == null || actorId.isBlank()) {
            return Optional.empty();
        }
        String normalizedColor = normalizeColor(color);
        if (normalizedColor == null) {
            record("REJECTED", "unsupported_color");
            return Optional.empty();
        }
        Instant timestamp = updatedAt == null ? clock.instant() : updatedAt;
        CustomerPreference preference = new CustomerPreference(
                null,
                actorId,
                PREFERRED_COLOR,
                normalizedColor,
                PreferenceScope.ACTOR,
                1.0,
                PreferenceOrigin.EXPLICIT_USER,
                true,
                timestamp,
                timestamp.plus(properties.effectiveTtl()));
        CustomerPreference saved = store.save(preference);
        record("SAVED", "explicit_confirmed");
        return Optional.of(saved);
    }

    public void clearConversation(UUID conversationId, String actorId) {
        if (!properties.enabled()) {
            return;
        }
        store.clearConversation(conversationId, actorId);
        record("CLEARED", "conversation");
    }

    public void clearActor(String actorId) {
        if (!properties.enabled()) {
            return;
        }
        store.clearActor(actorId);
        record("CLEARED", "actor");
    }

    private String normalizeColor(String color) {
        if (color == null) {
            return null;
        }
        String normalized = color.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > properties.effectiveMaxValueCharacters()
                || !ALLOWED_COLORS.contains(normalized)) {
            return null;
        }
        return normalized;
    }

    private void record(String result, String reason) {
        StructuredEventLog.info(log, "CUSTOMER_PREFERENCE_RECORDED", java.util.Map.of(
                "operation", "conversation.preference.store",
                "result", result,
                "reason", reason));
    }
}
