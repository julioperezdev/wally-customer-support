package com.wally.customersupport.conversation.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.wally.customersupport.conversation.application.port.out.ConversationSummarizer;
import com.wally.customersupport.conversation.domain.model.ConversationState;
import com.wally.customersupport.conversation.domain.model.ConversationSummary;
import com.wally.customersupport.shared.infrastructure.config.ConversationSummaryProperties;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Applies the controlled summary strategy while keeping the recent window and
 * typed transactional state authoritative.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationSummaryService {

    private final ConversationSummarizer summarizer;
    private final ConversationSummaryProperties properties;

    public String summaryForContext(ConversationState state) {
        if (!properties.enabled() || state == null || state.summary() == null) {
            return null;
        }
        return state.summary().text();
    }

    public ConversationState appendAndMaybeSummarize(
            ConversationState current,
            String latestMessage,
            Instant updatedAt) {
        if (current == null || latestMessage == null || latestMessage.isBlank()) {
            return current;
        }

        List<String> messages = new ArrayList<>(current.recentMessages());
        if (messages.isEmpty() || !latestMessage.equals(messages.getFirst())) {
            messages.addFirst(latestMessage);
        }

        if (!properties.enabled() || !thresholdReached(messages)) {
            return state(current, messages, updatedAt, current.summary());
        }

        int recentMessageCount = Math.min(
                properties.effectiveRecentMessageCount(),
                Math.max(1, messages.size() - 1));
        List<String> olderMessages = messages.subList(recentMessageCount, messages.size())
                .reversed();
        String previousSummary = current.summary() == null ? null : current.summary().text();
        long startedAt = System.nanoTime();

        try {
            String generated = limit(summarizer.summarize(previousSummary, olderMessages));
            if (generated == null || generated.isBlank()) {
                throw new IllegalStateException("conversation summarizer returned an empty summary");
            }

            ConversationSummary previous = current.summary();
            ConversationSummary summary = new ConversationSummary(
                    generated,
                    previous == null ? 1L : previous.version() + 1L,
                    (previous == null ? 0 : previous.summarizedMessageCount()) + olderMessages.size(),
                    updatedAt);
            StructuredEventLog.info(log, "CONVERSATION_SUMMARY_CREATED", Map.of(
                    "operation", "conversation.summary.create",
                    "result", "SUCCESS",
                    "correlationId", current.conversationId(),
                    "summaryVersion", summary.version(),
                    "summarizedMessageCount", summary.summarizedMessageCount(),
                    "recentMessageCount", recentMessageCount,
                    "summaryCharacters", summary.text().length(),
                    "durationMs", elapsedMillis(startedAt)));
            return state(current, messages.subList(0, recentMessageCount), updatedAt, summary);
        } catch (RuntimeException exception) {
            StructuredEventLog.warn(log, "CONVERSATION_SUMMARY_FALLBACK", Map.of(
                    "operation", "conversation.summary.create",
                    "result", "FALLBACK_RECENT_WINDOW",
                    "correlationId", current.conversationId(),
                    "errorType", exception.getClass().getSimpleName(),
                    "recentMessageCount", messages.size(),
                    "durationMs", elapsedMillis(startedAt)));
            return state(current, messages, updatedAt, current.summary());
        }
    }

    public void recordContextPrepared(ConversationState state) {
        int recentCharacters = state == null
                ? 0
                : state.recentMessages().stream().mapToInt(String::length).sum();
        String summary = summaryForContext(state);
        StructuredEventLog.info(log, "CONVERSATION_CONTEXT_PREPARED", Map.of(
                "operation", "conversation.context.prepare",
                "result", "SUCCESS",
                "correlationId", state == null ? "unknown" : state.conversationId(),
                "recentMessageCount", state == null ? 0 : state.recentMessages().size(),
                "recentCharacters", recentCharacters,
                "summaryPresent", summary != null && !summary.isBlank(),
                "summaryCharacters", summary == null ? 0 : summary.length(),
                "summaryEnabled", properties.enabled()));
    }

    private boolean thresholdReached(List<String> messages) {
        int characters = messages.stream().mapToInt(String::length).sum();
        return messages.size() > properties.effectiveTriggerMessageCount()
                || characters > properties.effectiveTriggerCharacters();
    }

    private String limit(String value) {
        if (value == null) {
            return null;
        }
        int max = properties.effectiveMaxSummaryCharacters();
        return value.length() <= max ? value.strip() : value.substring(0, max).strip();
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private static ConversationState state(
            ConversationState current,
            List<String> messages,
            Instant updatedAt,
            ConversationSummary summary) {
        return new ConversationState(
                current.conversationId(),
                current.actorId(),
                List.copyOf(messages),
                updatedAt,
                current.version(),
                summary);
    }
}
