package com.wally.customersupport.conversation.application.port.out;

import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationContext;

public interface TypeSafeUseCaseSelector {

    TypeSafeSelection select(ConversationContext context);

    record TypeSafeSelection(
            ConversationAction action,
            double confidence,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            String failureReason) {

        public boolean successful() {
            return failureReason == null
                    && action != null
                    && action != ConversationAction.NONE
                    && Double.isFinite(confidence)
                    && confidence >= 0.0
                    && confidence <= 1.0;
        }

        public static TypeSafeSelection failure(String reason) {
            return new TypeSafeSelection(null, 0.0, null, null, null,
                    reason == null || reason.isBlank() ? "CLIENT_ERROR" : reason);
        }
    }
}
