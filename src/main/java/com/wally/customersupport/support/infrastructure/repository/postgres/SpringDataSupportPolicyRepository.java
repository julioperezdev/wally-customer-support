package com.wally.customersupport.support.infrastructure.repository.postgres;

import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.support.infrastructure.repository.postgres.SupportPolicyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataSupportPolicyRepository extends JpaRepository<SupportPolicyJpaEntity, UUID> {

    Optional<SupportPolicyJpaEntity> findFirstByPolicyKeyAndActiveTrueOrderByVersionDesc(String policyKey);
}
