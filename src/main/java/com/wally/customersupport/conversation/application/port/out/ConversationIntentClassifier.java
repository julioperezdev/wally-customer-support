package com.wally.customersupport.conversation.application.port.out;

import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;

public interface ConversationIntentClassifier {

    ConversationIntentDecision classify(String message);
}
