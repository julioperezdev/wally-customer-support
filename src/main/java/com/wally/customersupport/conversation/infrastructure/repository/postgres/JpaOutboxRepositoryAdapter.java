package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.infrastructure.repository.postgres.OutboxJpaEntity;
import com.wally.customersupport.conversation.infrastructure.repository.postgres.SpringDataOutboxRepository;
import com.wally.customersupport.conversation.application.port.out.OutboxRepository;
import com.wally.customersupport.conversation.domain.model.OutboxMessage;
import com.wally.customersupport.conversation.domain.model.OutboxStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaOutboxRepositoryAdapter implements OutboxRepository {

    private final SpringDataOutboxRepository repository;

    public JpaOutboxRepositoryAdapter(SpringDataOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    public OutboxMessage save(OutboxMessage message) {
        return repository.save(new OutboxJpaEntity(message)).toDomain();
    }

    @Override
    public List<OutboxMessage> findDue(Instant now, int limit) {
        return repository.findByStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
                        List.of(OutboxStatus.PENDING),
                        now,
                        PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(OutboxJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void markProcessing(UUID id) {
        repository.findById(id).ifPresent(entity -> {
            entity.markProcessing();
            repository.save(entity);
        });
    }

    @Override
    @Transactional
    public void markSent(UUID id, Instant sentAt) {
        repository.findById(id).ifPresent(entity -> {
            entity.markSent(sentAt);
            repository.save(entity);
        });
    }

    @Override
    @Transactional
    public void markFailed(UUID id, String error, Instant availableAt, boolean exhausted) {
        repository.findById(id).ifPresent(entity -> {
            entity.markFailed(error, availableAt, exhausted);
            repository.save(entity);
        });
    }
}
