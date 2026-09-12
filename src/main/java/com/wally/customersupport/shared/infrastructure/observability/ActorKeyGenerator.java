package com.wally.customersupport.shared.infrastructure.observability;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.shared.infrastructure.config.ObservabilityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Creates a stable, non-reversible actor identifier for operational logs.
 *
 * <p>The external channel identifier is deliberately never returned or logged.
 * The server-side secret makes the value resistant to offline dictionary attacks
 * against known phone numbers or chat IDs.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActorKeyGenerator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObservabilityProperties properties;

    public Optional<String> generate(Channel channel, String externalCustomerId) {
        if (channel == null || externalCustomerId == null || externalCustomerId.isBlank()) {
            return Optional.empty();
        }

        String secret = properties.effectiveActorKeySecret();
        if (secret.isBlank()) {
            return Optional.empty();
        }

        String canonicalActor = channel.name().toLowerCase(Locale.ROOT) + ":" + externalCustomerId.strip();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Optional.of(HexFormat.of().formatHex(
                    mac.doFinal(canonicalActor.getBytes(StandardCharsets.UTF_8))));
        } catch (GeneralSecurityException exception) {
            // Observability must not break the customer-facing message flow.
            log.warn("Unable to generate pseudonymous actor key: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
