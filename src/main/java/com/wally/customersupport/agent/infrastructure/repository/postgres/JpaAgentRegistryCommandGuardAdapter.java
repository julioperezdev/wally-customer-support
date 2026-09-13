package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import com.wally.customersupport.agent.application.port.out.AgentRegistryCommandGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed atomic guard that never persists the raw idempotency key. */
@Component
@RequiredArgsConstructor
public class JpaAgentRegistryCommandGuardAdapter implements AgentRegistryCommandGuard {

    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final SpringDataAgentRegistryCommandClaimRepository repository;

    @Override
    @Transactional
    public boolean tryAcquire(String idempotencyKey) {
        String normalizedKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey").strip();
        if (normalizedKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        return repository.insertClaim(UUID.randomUUID(), sha256(normalizedKey)) == 1;
    }

    private static String sha256(String value) {
        try {
            return HEX_FORMAT.formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
