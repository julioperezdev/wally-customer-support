package com.wally.customersupport.conversation.domain.model;

/**
 * Allow-listed operation proposed by the conversational router.
 *
 * <p>The model may propose one of these values, but the application remains
 * responsible for validating arguments and executing the corresponding use
 * case.</p>
 */
public enum ConversationAction {
    NONE,
    GREETING,
    CATALOG_SEARCH,
    ADD_TO_CART,
    VIEW_CART,
    REMOVE_FROM_CART,
    CLEAR_CART,
    CONFIRM_CHECKOUT,
    CANCEL_CHECKOUT,
    PURCHASE_LINK,
    BUSINESS_HOURS,
    POLICY_QUERY,
    HUMAN_HANDOFF,
    GENERAL_SUPPORT,
    UNKNOWN;

    public static ConversationAction fromIntent(ConversationIntent intent) {
        if (intent == null) {
            return UNKNOWN;
        }
        return switch (intent) {
            case GREETING -> GREETING;
            case CATALOG_SEARCH -> CATALOG_SEARCH;
            case PURCHASE_LINK -> PURCHASE_LINK;
            case BUSINESS_HOURS -> BUSINESS_HOURS;
            case POLICY_QUERY -> POLICY_QUERY;
            case HUMAN_HANDOFF -> HUMAN_HANDOFF;
            case GENERAL_SUPPORT -> GENERAL_SUPPORT;
            case UNKNOWN -> UNKNOWN;
        };
    }

    public boolean isCartOperation() {
        return this == ADD_TO_CART
                || this == VIEW_CART
                || this == REMOVE_FROM_CART
                || this == CLEAR_CART
                || this == CONFIRM_CHECKOUT
                || this == CANCEL_CHECKOUT;
    }
}
