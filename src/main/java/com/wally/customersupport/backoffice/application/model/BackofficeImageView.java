package com.wally.customersupport.backoffice.application.model;

import java.time.Instant;
import java.util.UUID;

public record BackofficeImageView(
        UUID productId,
        String objectKey,
        String viewUrl,
        Instant expiresAt) {
}
