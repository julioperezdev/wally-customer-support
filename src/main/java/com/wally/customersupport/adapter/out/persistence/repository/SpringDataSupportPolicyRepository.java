package com.wally.customersupport.adapter.out.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.adapter.out.persistence.entity.SupportPolicyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataSupportPolicyRepository extends JpaRepository<SupportPolicyJpaEntity, UUID> {

    Optional<SupportPolicyJpaEntity> findFirstByPolicyKeyAndActiveTrueOrderByVersionDesc(String policyKey);
}
