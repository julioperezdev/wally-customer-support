package com.wally.customersupport.conversation.application.service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wally.customersupport.conversation.domain.model.CustomerPreference;
import com.wally.customersupport.shared.infrastructure.config.ConversationPreferenceProperties;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Captures and forgets only narrow, explicit, low-risk preference statements. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExplicitPreferenceCaptureService {

    private static final Pattern EXPLICIT_COLOR = Pattern.compile(
            "^(?:prefiero|me gusta|me encanta|mi color preferido es|mi color favorito es|recorda que me gusta) "
                    + "(?:(?:el|la) )?(?:(?:color) )?(?<value>[a-z0-9]+)$");
    private static final Pattern EXPLICIT_SIZE = Pattern.compile(
            "^(?:soy(?: de)?(?: talle| talla)?|uso(?: el)?(?: talle| talla)?|"
                    + "mi talle es|mi talla es|mi tamano es|prefiero(?: el talle| la talla| talle| talla)) "
                    + "(?<value>[a-z0-9]+(?: [a-z0-9]+)?)$");
    private static final Pattern FORGET_SIZE = Pattern.compile(
            "^(?:olvida|olvidate de|borra|elimina|no recuerdes) (?:mi )?(?:talle|talla|tamano)(?: de ropa)?$|"
                    + "^(?:olvida|olvidate de|borra|elimina|no recuerdes) que (?:soy|uso) "
                    + "(?:(?:talle|talla) )?(?:xs|s|m|l|xl|xxl)$");
    private static final Pattern FORGET_COLOR = Pattern.compile(
            "^(?:olvida|olvidate de|borra|elimina|no recuerdes) mi color(?: preferido| favorito)?$|"
                    + "^(?:olvida|olvidate de|borra|elimina|no recuerdes) que prefiero "
                    + "(?:(?:el|la) )?[a-z0-9]+$");
    private static final Pattern FORGET_ALL = Pattern.compile(
            "^(?:olvida|borra|elimina) (?:todas )?mis preferencias$|^no recuerdes mis preferencias$");
    private static final Pattern AMBIGUOUS_REJECTION = Pattern.compile(
            "^(?:no quiero|no me gusta) (?:esto|eso)$");

    private final CustomerPreferenceService customerPreferenceService;
    private final ConversationPreferenceProperties properties;

    public CaptureResult capture(String actorId, UUID conversationId, String message, Instant occurredAt) {
        String normalized = normalize(message);
        if (normalized == null) {
            record("NOT_DETECTED", "empty_message");
            return CaptureResult.notDetected();
        }
        if (AMBIGUOUS_REJECTION.matcher(normalized).matches()) {
            record("CLARIFICATION_REQUIRED", "ambiguous_reference");
            return CaptureResult.clarificationRequired();
        }

        String forgetKey = FORGET_SIZE.matcher(normalized).matches()
                ? CustomerPreferenceService.PREFERRED_SIZE
                : FORGET_COLOR.matcher(normalized).matches()
                        ? CustomerPreferenceService.PREFERRED_COLOR
                        : null;
        boolean forgetAll = FORGET_ALL.matcher(normalized).matches();
        PreferenceStatement statement = parseExplicitStatement(message).orElse(null);
        if (forgetKey == null && !forgetAll && statement == null) {
            record("NOT_DETECTED", "not_explicit");
            return CaptureResult.notDetected();
        }
        if (!properties.enabled()) {
            record("DISABLED", "feature_disabled");
            return CaptureResult.disabled();
        }
        if (forgetAll) {
            customerPreferenceService.clearActor(actorId);
            record("FORGOTTEN", "explicit_all_preferences");
            return CaptureResult.forgotten("all");
        }
        if (forgetKey != null) {
            customerPreferenceService.forgetExplicitPreference(actorId, conversationId, forgetKey);
            record("FORGOTTEN", forgetKey);
            return CaptureResult.forgotten(forgetKey);
        }

        Optional<CustomerPreference> saved = CustomerPreferenceService.PREFERRED_SIZE.equals(statement.key())
                ? customerPreferenceService.recordExplicitSize(actorId, statement.value(), occurredAt)
                : customerPreferenceService.recordExplicitColor(actorId, statement.value(), occurredAt);
        if (saved.isPresent()) {
            record("SAVED", statement.key());
            return CaptureResult.saved(statement.key(), saved.get().value(), statement.standalone());
        }
        record("REJECTED", statement.key());
        return CaptureResult.rejected(statement.key(), statement.value(), statement.standalone());
    }

    private static Optional<PreferenceStatement> parseExplicitStatement(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String[] rawClauses = message.split("[;,.!?\\n]+", -1);
        java.util.List<String> clauses = java.util.Arrays.stream(rawClauses)
                .map(ExplicitPreferenceCaptureService::normalize)
                .filter(java.util.Objects::nonNull)
                .toList();
        for (String clause : clauses) {
            Matcher size = EXPLICIT_SIZE.matcher(clause);
            if (size.matches()) {
                return Optional.of(new PreferenceStatement(
                        CustomerPreferenceService.PREFERRED_SIZE,
                        size.group("value"),
                        clauses.size() == 1));
            }
            Matcher color = EXPLICIT_COLOR.matcher(clause);
            if (color.matches()) {
                return Optional.of(new PreferenceStatement(
                        CustomerPreferenceService.PREFERRED_COLOR,
                        color.group("value"),
                        clauses.size() == 1));
            }
        }
        return Optional.empty();
    }

    private static String normalize(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return Normalizer.normalize(message, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void record(String result, String reason) {
        StructuredEventLog.info(log, "CUSTOMER_PREFERENCE_CAPTURED", java.util.Map.of(
                "operation", "conversation.preference.capture",
                "result", result,
                "reason", reason));
    }

    private record PreferenceStatement(String key, String value, boolean standalone) { }

    public record CaptureResult(Status status, String key, String value, boolean acknowledge) {

        public static CaptureResult notDetected() {
            return new CaptureResult(Status.NOT_DETECTED, null, null, false);
        }

        public static CaptureResult disabled() {
            return new CaptureResult(Status.DISABLED, null, null, false);
        }

        public static CaptureResult saved(String key, String value, boolean acknowledge) {
            return new CaptureResult(Status.SAVED, key, value, acknowledge);
        }

        public static CaptureResult rejected(String key, String value, boolean acknowledge) {
            return new CaptureResult(Status.REJECTED, key, value, acknowledge);
        }

        public static CaptureResult forgotten(String key) {
            return new CaptureResult(Status.FORGOTTEN, key, null, true);
        }

        public static CaptureResult clarificationRequired() {
            return new CaptureResult(Status.CLARIFICATION_REQUIRED, null, null, true);
        }

        public boolean shouldAcknowledge() { return acknowledge; }
    }

    public enum Status {
        NOT_DETECTED,
        DISABLED,
        SAVED,
        REJECTED,
        FORGOTTEN,
        CLARIFICATION_REQUIRED
    }
}
