package com.wally.customersupport.conversation.application.service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wally.customersupport.conversation.domain.model.CustomerPreference;
import com.wally.customersupport.shared.infrastructure.config.ConversationPreferenceProperties;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Captures only a narrow, explicit preference language contract.
 *
 * <p>A color mentioned as part of a catalog query is deliberately not a
 * preference. This service does not use an LLM to infer or persist memory.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExplicitPreferenceCaptureService {

    private static final Pattern EXPLICIT_COLOR = Pattern.compile(
            "^(?:prefiero|me gusta|me encanta|mi color preferido es|mi color favorito es|recorda que me gusta) "
                    + "(?:(?:el|la) )?(?:color )?(?<color>[a-z0-9]+)$");

    private final CustomerPreferenceService customerPreferenceService;
    private final ConversationPreferenceProperties properties;

    public CaptureResult capture(String actorId, String message, Instant occurredAt) {
        Optional<String> color = parseExplicitColor(message);
        if (color.isEmpty()) {
            record("NOT_DETECTED", "not_explicit");
            return CaptureResult.notDetected();
        }
        if (!properties.enabled()) {
            record("DISABLED", "feature_disabled");
            return CaptureResult.disabled();
        }

        Optional<CustomerPreference> saved = customerPreferenceService.recordExplicitColor(
                actorId, color.get(), occurredAt);
        if (saved.isPresent()) {
            record("SAVED", "explicit_color");
            return CaptureResult.saved(color.get());
        }
        record("REJECTED", "unsupported_color");
        return CaptureResult.rejected(color.get());
    }

    private static Optional<String> parseExplicitColor(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String normalized = Normalizer.normalize(message, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        Matcher matcher = EXPLICIT_COLOR.matcher(normalized);
        return matcher.matches() ? Optional.of(matcher.group("color")) : Optional.empty();
    }

    private void record(String result, String reason) {
        StructuredEventLog.info(log, "CUSTOMER_PREFERENCE_CAPTURED", java.util.Map.of(
                "operation", "conversation.preference.capture",
                "result", result,
                "reason", reason));
    }

    public record CaptureResult(Status status, String color) {

        public static CaptureResult notDetected() {
            return new CaptureResult(Status.NOT_DETECTED, null);
        }

        public static CaptureResult disabled() {
            return new CaptureResult(Status.DISABLED, null);
        }

        public static CaptureResult saved(String color) {
            return new CaptureResult(Status.SAVED, color);
        }

        public static CaptureResult rejected(String color) {
            return new CaptureResult(Status.REJECTED, color);
        }

        public boolean shouldAcknowledge() {
            return status == Status.SAVED || status == Status.REJECTED;
        }
    }

    public enum Status {
        NOT_DETECTED,
        DISABLED,
        SAVED,
        REJECTED
    }
}
