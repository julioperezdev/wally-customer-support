package com.wally.customersupport.adapter.out.persistence.repository;

import java.util.List;
import java.util.UUID;

import com.wally.customersupport.adapter.out.persistence.entity.BusinessHourJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataBusinessHourRepository extends JpaRepository<BusinessHourJpaEntity, UUID> {

    List<BusinessHourJpaEntity> findByActiveTrueOrderByDayOfWeekAsc();
}
