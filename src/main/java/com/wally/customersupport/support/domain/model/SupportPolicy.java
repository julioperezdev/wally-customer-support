package com.wally.customersupport.support.domain.model;

import java.time.Instant;
import java.util.UUID;

public record SupportPolicy(
        UUID id,
        String policyKey,
        String title,
        String content,
        boolean active,
        boolean demo,
        int version,
        Instant publishedAt) {
}
