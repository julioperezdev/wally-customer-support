package com.wally.customersupport.support.infrastructure.repository.postgres;

import java.util.List;
import java.util.Optional;

import com.wally.customersupport.support.infrastructure.repository.postgres.SpringDataBusinessHourRepository;
import com.wally.customersupport.support.infrastructure.repository.postgres.SpringDataSupportPolicyRepository;
import com.wally.customersupport.support.application.port.out.SupportConfigurationRepository;
import com.wally.customersupport.support.domain.model.BusinessHour;
import com.wally.customersupport.support.domain.model.SupportPolicy;
import org.springframework.stereotype.Repository;

@Repository
public class JpaSupportConfigurationRepositoryAdapter implements SupportConfigurationRepository {

    private final SpringDataBusinessHourRepository businessHourRepository;
    private final SpringDataSupportPolicyRepository policyRepository;

    public JpaSupportConfigurationRepositoryAdapter(
            SpringDataBusinessHourRepository businessHourRepository,
            SpringDataSupportPolicyRepository policyRepository) {
        this.businessHourRepository = businessHourRepository;
        this.policyRepository = policyRepository;
    }

    @Override
    public List<BusinessHour> findBusinessHours() {
        return businessHourRepository.findByActiveTrueOrderByDayOfWeekAsc().stream()
                .map(entity -> entity.toDomain())
                .toList();
    }

    @Override
    public Optional<SupportPolicy> findActivePolicy(String policyKey) {
        return policyRepository.findFirstByPolicyKeyAndActiveTrueOrderByVersionDesc(policyKey)
                .map(entity -> entity.toDomain());
    }
}
