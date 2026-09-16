package com.wally.customersupport.conversation.domain.model;

public enum ConversationExecutionAction {
    DIRECT_RESPONSE,
    CART,
    PURCHASE_DEFERRED,
    CATALOG_SEARCH,
    PURCHASE_LINK,
    BUSINESS_HOURS,
    POLICY_QUERY,
    GENERAL_SUPPORT,
    HUMAN_HANDOFF,
    LOW_CONFIDENCE,
    SAFE_FALLBACK
}
