package com.wally.customersupport.support.infrastructure.repository.postgres;

import java.util.List;
import java.util.UUID;

import com.wally.customersupport.support.infrastructure.repository.postgres.BusinessHourJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataBusinessHourRepository extends JpaRepository<BusinessHourJpaEntity, UUID> {

    List<BusinessHourJpaEntity> findByActiveTrueOrderByDayOfWeekAsc();
}
