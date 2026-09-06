package com.wally.customersupport.conversation.application.port.out;

import java.util.List;

import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;

public interface ConversationIntentClassifier {

    ConversationIntentDecision classify(ConversationContext context);

    default ConversationIntentDecision classify(String message) {
        return classify(new ConversationContext(null, null, message, List.of(), List.of()));
    }
}
