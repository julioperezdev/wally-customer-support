package com.wally.customersupport.conversation.infrastructure.ai.mock;

import java.util.List;
import java.util.stream.Collectors;

import com.wally.customersupport.conversation.application.port.out.ConversationSummarizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic local/test double. It preserves the summary boundary without
 * calling an external model.
 */
@Component
@ConditionalOnProperty(name = "wcs.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockConversationSummarizer implements ConversationSummarizer {

    @Override
    public String summarize(String previousSummary, List<String> olderMessages) {
        String messages = olderMessages == null
                ? ""
                : olderMessages.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::strip)
                        .collect(Collectors.joining(" | "));
        if (previousSummary == null || previousSummary.isBlank()) {
            return "Contexto previo: " + messages;
        }
        return previousSummary.strip() + " | Nuevos temas: " + messages;
    }
}
