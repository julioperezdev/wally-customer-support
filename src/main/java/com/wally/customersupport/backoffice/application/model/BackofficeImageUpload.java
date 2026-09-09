package com.wally.customersupport.backoffice.application.model;

import java.time.Instant;
import java.util.UUID;

public record BackofficeImageUpload(
        UUID productId,
        String objectKey,
        String uploadUrl,
        Instant expiresAt) {
}
