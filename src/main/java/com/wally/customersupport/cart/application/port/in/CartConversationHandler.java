package com.wally.customersupport.cart.application.port.in;

import java.util.Optional;

import com.wally.customersupport.conversation.domain.model.ConversationContext;

public interface CartConversationHandler {

    default boolean recognizes(String message) {
        return false;
    }

    Optional<Response> handle(ConversationContext context);

    record Response(String text) {
    }
}
