package com.wally.customersupport.conversation.domain.model;

/** Outcome of the most recent authoritative catalog lookup in a conversation. */
public enum CatalogObservationStatus {
    MATCHED,
    NO_MATCH,
    CLARIFICATION,
    AMBIGUOUS,
    ALTERNATIVES,
    UNSUPPORTED_CATEGORY,
    CLEARED
}
