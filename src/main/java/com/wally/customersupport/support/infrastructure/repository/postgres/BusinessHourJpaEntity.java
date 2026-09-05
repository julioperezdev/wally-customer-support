package com.wally.customersupport.support.infrastructure.repository.postgres;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import com.wally.customersupport.support.domain.model.BusinessHour;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "business_hours", schema = "wcs")
public class BusinessHourJpaEntity {

    @Id
    private UUID id;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "opens_at")
    private LocalTime opensAt;

    @Column(name = "closes_at")
    private LocalTime closesAt;

    @Column(nullable = false)
    private boolean closed;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean demo;

    @Column(name = "record_version", nullable = false)
    private int version;

    protected BusinessHourJpaEntity() {
    }

    public BusinessHour toDomain() {
        return new BusinessHour(id, dayOfWeek, opensAt, closesAt, closed,
                ZoneId.of(timezone), active, demo, version);
    }
}
