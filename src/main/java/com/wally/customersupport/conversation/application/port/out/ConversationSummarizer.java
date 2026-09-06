package com.wally.customersupport.conversation.application.port.out;

import java.util.List;

/**
 * Summarizes older conversational context without exposing provider details to
 * the application or domain.
 */
public interface ConversationSummarizer {

    String summarize(String previousSummary, List<String> olderMessages);
}
