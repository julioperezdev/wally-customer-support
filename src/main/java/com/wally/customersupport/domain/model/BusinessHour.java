package com.wally.customersupport.domain.model;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

public record BusinessHour(
        UUID id,
        int dayOfWeek,
        LocalTime opensAt,
        LocalTime closesAt,
        boolean closed,
        ZoneId timezone,
        boolean active,
        boolean demo,
        int version) {
}
