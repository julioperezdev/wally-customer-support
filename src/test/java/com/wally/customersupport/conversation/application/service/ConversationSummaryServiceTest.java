package com.wally.customersupport.conversation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.out.ConversationSummarizer;
import com.wally.customersupport.conversation.domain.model.ConversationState;
import com.wally.customersupport.shared.infrastructure.config.ConversationSummaryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationSummaryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-06T12:00:00Z");
    private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000036");

    @Mock
    private ConversationSummarizer summarizer;

    private ConversationSummaryService service;

    @BeforeEach
    void setUp() {
        service = new ConversationSummaryService(
                summarizer,
                new ConversationSummaryProperties(true, 3, 2, 10_000, 200));
    }

    @Test
    void doesNotSummarizeBelowConfiguredThreshold() {
        ConversationState state = state(List.of("último", "anterior"));

        ConversationState result = service.appendAndMaybeSummarize(state, "actual", NOW);

        assertNull(result.summary());
        assertEquals(List.of("actual", "último", "anterior"), result.recentMessages());
    }

    @Test
    void keepsRecentWindowAndVersionsSummaryWhenThresholdIsReached() {
        when(summarizer.summarize(null, List.of("viejo", "anterior", "último")))
                .thenReturn("El cliente consultó productos.");
        ConversationState state = state(List.of("nuevo", "último", "anterior", "viejo"));

        ConversationState result = service.appendAndMaybeSummarize(state, "actual", NOW);

        assertEquals(List.of("actual", "nuevo"), result.recentMessages());
        assertEquals("El cliente consultó productos.", result.summary().text());
        assertEquals(1L, result.summary().version());
        assertEquals(3, result.summary().summarizedMessageCount());
        verify(summarizer).summarize(null, List.of("viejo", "anterior", "último"));
    }

    @Test
    void fallsBackWithoutReplacingExistingSummaryWhenSummarizerFails() {
        when(summarizer.summarize(any(), any())).thenThrow(new IllegalStateException("synthetic failure"));
        ConversationState state = new ConversationState(
                CONVERSATION_ID,
                "actor",
                List.of("uno", "dos", "tres"),
                NOW,
                0L,
                new com.wally.customersupport.conversation.domain.model.ConversationSummary(
                        "Resumen anterior", 2L, 1, NOW));

        ConversationState result = service.appendAndMaybeSummarize(state, "cuatro", NOW);

        assertEquals("Resumen anterior", result.summary().text());
        assertEquals(List.of("cuatro", "uno", "dos", "tres"), result.recentMessages());
    }

    private static ConversationState state(List<String> messages) {
        return new ConversationState(CONVERSATION_ID, "actor", messages, NOW);
    }
}
