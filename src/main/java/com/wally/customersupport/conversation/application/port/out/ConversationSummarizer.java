package com.wally.customersupport.conversation.application.port.out;

import java.util.List;

import com.wally.customersupport.conversation.domain.model.Channel;

/**
 * Summarizes older conversational context without exposing provider details to
 * the application or domain.
 */
public interface ConversationSummarizer {

    String summarize(String previousSummary, List<String> olderMessages);

    /** Channel-aware profile selection; existing implementations remain compatible. */
    default String summarize(String previousSummary, List<String> olderMessages, Channel channel) {
        return summarize(previousSummary, olderMessages);
    }
}
