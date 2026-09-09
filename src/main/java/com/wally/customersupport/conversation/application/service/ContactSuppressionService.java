package com.wally.customersupport.conversation.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import com.wally.customersupport.conversation.application.port.out.ContactSuppressionRepository;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ContactSuppression;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactSuppressionService {

    private final ContactSuppressionRepository repository;

    public boolean isSuppressed(Channel channel, String externalCustomerId) {
        String actorKey = actorKey(channel, externalCustomerId);
        return actorKey != null && repository.existsActiveByActorKey(actorKey);
    }

    public void suppress(
            Channel channel,
            String externalCustomerId,
            java.util.UUID sourceMessageId,
            Instant now) {
        String actorKey = actorKey(channel, externalCustomerId);
        if (actorKey == null || sourceMessageId == null || now == null) {
            return;
        }
        repository.saveIfAbsent(ContactSuppression.doNotContact(actorKey, sourceMessageId, now));
        StructuredEventLog.info(log, "CONTACT_SUPPRESSED", java.util.Map.of(
                "operation", "conversation.contact.suppression",
                "status", "DO_NOT_CONTACT",
                "reason", "USER_REQUEST",
                "correlationId", sourceMessageId));
    }

    public String actorKey(Channel channel, String externalCustomerId) {
        if (channel == null || externalCustomerId == null || externalCustomerId.isBlank()) {
            return null;
        }
        return sha256(channel.name() + ":" + externalCustomerId.strip());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte current : digest) {
                result.append(String.format("%02x", current));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
