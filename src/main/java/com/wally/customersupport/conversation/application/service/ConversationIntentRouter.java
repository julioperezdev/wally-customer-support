package com.wally.customersupport.conversation.application.service;

import com.wally.customersupport.conversation.application.port.out.ConversationIntentClassifier;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Owns only the LLM/classifier call. It produces an untrusted proposal; it
 * does not merge state, choose a tool or execute a business operation.
 */
@Service
@RequiredArgsConstructor
public final class ConversationIntentRouter {

    private final ConversationIntentClassifier classifier;

    public ConversationIntentDecision classify(ConversationContext context) {
        return classifier.classify(context);
    }
}
