package com.wally.customersupport.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import com.wally.customersupport.adapter.out.persistence.repository.SpringDataBusinessHourRepository;
import com.wally.customersupport.adapter.out.persistence.repository.SpringDataSupportPolicyRepository;
import com.wally.customersupport.application.port.out.SupportConfigurationRepository;
import com.wally.customersupport.domain.model.BusinessHour;
import com.wally.customersupport.domain.model.SupportPolicy;
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
