package com.wally.customersupport.support.application.port.out;

import java.util.List;
import java.util.Optional;

import com.wally.customersupport.support.domain.model.BusinessHour;
import com.wally.customersupport.support.domain.model.SupportPolicy;

public interface SupportConfigurationRepository {

    List<BusinessHour> findBusinessHours();

    Optional<SupportPolicy> findActivePolicy(String policyKey);
}
