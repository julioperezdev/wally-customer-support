package com.wally.customersupport.application.service;

import java.util.List;
import java.util.Optional;

import com.wally.customersupport.application.port.out.SupportConfigurationRepository;
import com.wally.customersupport.domain.model.BusinessHour;
import com.wally.customersupport.domain.model.SupportPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportConfigurationQueryService {

    private final SupportConfigurationRepository repository;

    public SupportConfigurationQueryService(SupportConfigurationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<BusinessHour> businessHours() {
        return List.copyOf(repository.findBusinessHours());
    }

    @Transactional(readOnly = true)
    public Optional<SupportPolicy> activePolicy(String policyKey) {
        if (policyKey == null || policyKey.isBlank()) {
            return Optional.empty();
        }
        return repository.findActivePolicy(policyKey.trim());
    }
}
