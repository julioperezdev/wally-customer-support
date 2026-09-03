package com.wally.customersupport.application.port.out;

import java.util.List;
import java.util.Optional;

import com.wally.customersupport.domain.model.BusinessHour;
import com.wally.customersupport.domain.model.SupportPolicy;

public interface SupportConfigurationRepository {

    List<BusinessHour> findBusinessHours();

    Optional<SupportPolicy> findActivePolicy(String policyKey);
}
