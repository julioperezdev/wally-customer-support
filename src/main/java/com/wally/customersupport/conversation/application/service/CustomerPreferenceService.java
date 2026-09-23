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
    public static final String PREFERRED_SIZE = "preferred_size";

    private static final List<String> ALLOWED_COLORS = List.of(
            "negro", "blanco", "gris", "azul", "rojo", "verde", "amarillo", "rosa", "violeta");
    private static final List<String> ALLOWED_SIZES = List.of("XS", "S", "M", "L", "XL", "XXL");

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
        String normalizedColor = normalizeColor(color);
        return recordExplicit(actorId, PREFERRED_COLOR, normalizedColor, updatedAt, "unsupported_color");
    }

    public Optional<CustomerPreference> recordExplicitSize(
            String actorId,
            String size,
            Instant updatedAt) {
        String normalizedSize = normalizeSize(size);
        return recordExplicit(actorId, PREFERRED_SIZE, normalizedSize, updatedAt, "unsupported_size");
    }

    public int forgetExplicitPreference(String actorId, UUID conversationId, String key) {
        if (!properties.enabled() || actorId == null || actorId.isBlank() || !isSupportedKey(key)) {
            return 0;
        }
        int deleted = store.deletePreference(actorId.strip(), conversationId, key);
        StructuredEventLog.info(log, "CUSTOMER_PREFERENCE_FORGOTTEN", java.util.Map.of(
                "operation", "conversation.preference.forget",
                "result", deleted > 0 ? "DELETED" : "NOT_FOUND",
                "preferenceKey", key,
                "deletedCount", deleted));
        return deleted;
    }

    private Optional<CustomerPreference> recordExplicit(
            String actorId,
            String key,
            String normalizedValue,
            Instant updatedAt,
            String rejectionReason) {
        if (!properties.enabled() || actorId == null || actorId.isBlank()) {
            return Optional.empty();
        }
        if (normalizedValue == null) {
            record("REJECTED", rejectionReason);
            return Optional.empty();
        }
        Instant timestamp = updatedAt == null ? clock.instant() : updatedAt;
        CustomerPreference preference = new CustomerPreference(
                null,
                actorId,
                key,
                normalizedValue,
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

    private String normalizeSize(String size) {
        if (size == null) {
            return null;
        }
        String normalized = size.strip().toUpperCase(Locale.ROOT);
        normalized = switch (normalized) {
            case "EXTRA SMALL", "SMALL", "PEQUENO", "PEQUENA", "CHICO", "CHICA" -> "S";
            case "MEDIUM", "MEDIANO", "MEDIANA" -> "M";
            case "LARGE", "GRANDE", "EXTRA GRANDE" -> "L";
            case "EXTRA LARGE" -> "XL";
            default -> normalized;
        };
        if (normalized.length() > properties.effectiveMaxValueCharacters() || !ALLOWED_SIZES.contains(normalized)) {
            return null;
        }
        return normalized;
    }

    private static boolean isSupportedKey(String key) {
        return PREFERRED_COLOR.equals(key) || PREFERRED_SIZE.equals(key);
    }

    private void record(String result, String reason) {
        StructuredEventLog.info(log, "CUSTOMER_PREFERENCE_RECORDED", java.util.Map.of(
                "operation", "conversation.preference.store",
                "result", result,
                "reason", reason));
    }
}
