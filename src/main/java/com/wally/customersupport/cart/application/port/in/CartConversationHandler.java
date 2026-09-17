package com.wally.customersupport.cart.application.port.in;

import java.util.Optional;

import com.wally.customersupport.cart.application.service.CartCommandParser;
import com.wally.customersupport.conversation.domain.model.ConversationContext;

public interface CartConversationHandler {

    default boolean recognizes(String message) {
        return false;
    }

    Optional<Response> handle(ConversationContext context);

    /**
     * Executes a previously validated structured cart action. The default
     * delegates to the legacy text parser so existing adapters remain safe
     * while the router is introduced incrementally.
     */
    default Optional<Response> handle(
            ConversationContext context,
            CartCommandParser.Command command) {
        return handle(context);
    }

    record Response(String text) {
    }
}
