package com.wally.customersupport.backoffice.application.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BackofficeHumanFollowUp(
        UUID id,
        UUID conversationId,
        String channel,
        String reason,
        String priority,
        String status,
        Instant dueAt,
        String assignedTo,
        List<String> contextPreview) {

    public BackofficeHumanFollowUp {
        contextPreview = contextPreview == null ? List.of() : List.copyOf(contextPreview);
    }
}
