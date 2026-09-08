package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.infrastructure.repository.postgres.ProcessingAttemptJpaEntity;
import com.wally.customersupport.conversation.infrastructure.repository.postgres.SpringDataProcessingAttemptRepository;
import com.wally.customersupport.conversation.application.port.out.ProcessingAttemptRepository;
import com.wally.customersupport.conversation.domain.model.ProcessingAttempt;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaProcessingAttemptRepositoryAdapter implements ProcessingAttemptRepository {

    private final SpringDataProcessingAttemptRepository repository;

    public JpaProcessingAttemptRepositoryAdapter(SpringDataProcessingAttemptRepository repository) {
        this.repository = repository;
    }

    @Override
    public ProcessingAttempt save(ProcessingAttempt attempt) {
        return repository.save(new ProcessingAttemptJpaEntity(attempt)).toDomain();
    }

    @Override
    @Transactional
    public List<ProcessingAttempt> findDue(Instant now, int limit, Duration leaseDuration) {
        repository.requeueStale(now.minus(leaseDuration), now);
        return repository.findDue(now, PageRequest.of(0, Math.max(1, limit))).stream()
                .map(ProcessingAttemptJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public boolean claim(UUID id, Instant now) {
        return repository.claim(id, now) == 1;
    }

    @Override
    @Transactional
    public void markCompleted(UUID id, Instant now) {
        repository.findById(id).ifPresent(entity -> {
            entity.markCompleted(now);
            repository.save(entity);
        });
    }

    @Override
    @Transactional
    public void markFailed(UUID id, String error, Instant availableAt, boolean exhausted, Instant now) {
        repository.findById(id).ifPresent(entity -> {
            entity.markFailed(error, availableAt, exhausted, now);
            repository.save(entity);
        });
    }
}
