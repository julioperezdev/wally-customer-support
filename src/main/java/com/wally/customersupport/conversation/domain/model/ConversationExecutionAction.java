package com.wally.customersupport.conversation.domain.model;

public enum ConversationExecutionAction {
    DIRECT_RESPONSE,
    CATALOG_SEARCH,
    BUSINESS_HOURS,
    POLICY_QUERY,
    GENERAL_SUPPORT,
    HUMAN_HANDOFF,
    LOW_CONFIDENCE,
    SAFE_FALLBACK
}
