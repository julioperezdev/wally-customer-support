package com.wally.customersupport.order.infrastructure.repository.postgres;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPaymentEventRepository extends JpaRepository<PaymentEventJpaEntity, UUID> {

    boolean existsByProviderAndProviderEventId(String provider, String providerEventId);
}
