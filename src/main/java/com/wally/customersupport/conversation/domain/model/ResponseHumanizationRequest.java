package com.wally.customersupport.conversation.domain.model;

import java.util.Objects;

import com.wally.customersupport.catalog.application.service.CatalogSearchResult;

/** Input boundary for rendering a validated response. */
public record ResponseHumanizationRequest(
        String useCase,
        Channel channel,
        CatalogSearchResult catalogResult) {

    public ResponseHumanizationRequest {
        useCase = required(useCase, "useCase");
        channel = Objects.requireNonNull(channel, "channel");
        catalogResult = Objects.requireNonNull(catalogResult, "catalogResult");
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
