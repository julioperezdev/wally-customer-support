package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import com.wally.customersupport.agent.application.port.out.AgentEvaluationTriggerExecutionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed atomic guard that never persists the raw idempotency key. */
@Component
@RequiredArgsConstructor
public class JpaAgentEvaluationTriggerExecutionGuardAdapter
        implements AgentEvaluationTriggerExecutionGuard {

    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final SpringDataAgentEvaluationTriggerClaimRepository repository;

    @Override
    @Transactional
    public boolean tryAcquire(String idempotencyKey) {
        String normalizedKey = requiredKey(idempotencyKey);
        String keyHash = sha256(normalizedKey);
        return repository.insertClaim(UUID.randomUUID(), keyHash) == 1;
    }

    private static String requiredKey(String value) {
        String normalized = Objects.requireNonNull(value, "idempotencyKey").strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        return normalized;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HEX_FORMAT.formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
