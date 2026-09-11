package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.Optional;

import com.wally.customersupport.conversation.application.port.out.ContactSuppressionRepository;
import com.wally.customersupport.conversation.domain.model.ContactSuppression;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JpaContactSuppressionRepository implements ContactSuppressionRepository {

    private final SpringDataContactSuppressionRepository repository;

    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveByActorKey(String actorKey) {
        return actorKey != null && repository.existsByActorKeyAndStatus(actorKey, "DO_NOT_CONTACT");
    }

    @Override
    @Transactional
    public ContactSuppression saveIfAbsent(ContactSuppression suppression) {
        repository.insertIfAbsent(
                suppression.id(),
                suppression.actorKey(),
                suppression.status(),
                suppression.reason(),
                suppression.sourceMessageId(),
                suppression.createdAt(),
                suppression.updatedAt());
        return repository.findByActorKey(suppression.actorKey())
                .map(ContactSuppressionJpaEntity::toDomain)
                .orElseThrow(() -> new IllegalStateException("Contact suppression was not persisted"));
    }

    @Override
    @Transactional
    public boolean reactivate(String actorKey, Instant now) {
        return actorKey != null && now != null && repository.reactivate(actorKey, now) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> findLastReactivationAt(String actorKey) {
        return repository.findByActorKeyAndStatusAndReason(
                        actorKey, "REVOKED", "USER_REACTIVATED")
                .map(ContactSuppressionJpaEntity::updatedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContactSuppression> findActiveByActorKey(String actorKey) {
        return repository.findByActorKeyAndStatus(actorKey, "DO_NOT_CONTACT")
                .map(ContactSuppressionJpaEntity::toDomain);
    }
}
