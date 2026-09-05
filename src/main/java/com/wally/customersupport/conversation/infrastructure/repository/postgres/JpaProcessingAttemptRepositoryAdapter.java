package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import com.wally.customersupport.conversation.infrastructure.repository.postgres.ProcessingAttemptJpaEntity;
import com.wally.customersupport.conversation.infrastructure.repository.postgres.SpringDataProcessingAttemptRepository;
import com.wally.customersupport.conversation.application.port.out.ProcessingAttemptRepository;
import com.wally.customersupport.conversation.domain.model.ProcessingAttempt;
import org.springframework.stereotype.Repository;

@Repository
public class JpaProcessingAttemptRepositoryAdapter implements ProcessingAttemptRepository {

    private final SpringDataProcessingAttemptRepository repository;

    public JpaProcessingAttemptRepositoryAdapter(SpringDataProcessingAttemptRepository repository) {
        this.repository = repository;
    }

    @Override
    public ProcessingAttempt save(ProcessingAttempt attempt) {
        repository.save(new ProcessingAttemptJpaEntity(attempt));
        return attempt;
    }
}
