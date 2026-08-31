package com.wally.customersupport.adapter.out.persistence;

import com.wally.customersupport.adapter.out.persistence.entity.ProcessingAttemptJpaEntity;
import com.wally.customersupport.adapter.out.persistence.repository.SpringDataProcessingAttemptRepository;
import com.wally.customersupport.application.port.out.ProcessingAttemptRepository;
import com.wally.customersupport.domain.model.ProcessingAttempt;
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
