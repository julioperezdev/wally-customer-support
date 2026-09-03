package com.wally.customersupport.application.port.out;

import com.wally.customersupport.domain.model.ConversationIntentDecision;

public interface ConversationIntentClassifier {

    ConversationIntentDecision classify(String message);
}
