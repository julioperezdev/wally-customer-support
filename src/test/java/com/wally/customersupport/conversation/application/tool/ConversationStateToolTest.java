package com.wally.customersupport.conversation.application.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.application.service.ConversationSelectionStateService;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationSelection;
import com.wally.customersupport.conversation.domain.model.ConversationState;
import org.junit.jupiter.api.Test;

class ConversationStateToolTest {

    private static final Instant NOW = Instant.parse("2026-09-19T01:00:00Z");

    @Test
    void mergesASelectionAndResetClearsThePreviousIntentAndSku() {
        ConversationState current = new ConversationState(
                UUID.randomUUID(),
                "actor-hash",
                List.of("quiero una remera"),
                NOW,
                3,
                null,
                new ConversationSelection(
                        ConversationIntent.CATALOG_SEARCH,
                        ConversationAction.CATALOG_SEARCH,
                        new CatalogQuery("Remera NullPointer", "RP-REM-NP-NEG-M", null, "Negro"),
                        "RP-REM-NP-NEG-M",
                        "CATALOG_SEARCH"));
        ConversationStateTool tool = new ConversationStateTool(
                new ConversationSelectionStateService(),
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));

        ConversationStateTool.Result merged = tool.execute(new ConversationStateTool.Input(
                current, ConversationStateTool.Operation.MERGE_SELECTION, "size", "M"));
        ConversationStateTool.Result reset = tool.execute(new ConversationStateTool.Input(
                merged.nextState(), ConversationStateTool.Operation.RESET, null, null));

        assertThat(merged.status()).isEqualTo(ConversationStateTool.Status.UPDATED);
        assertThat(merged.nextState().selection().catalogQuery().size()).isEqualTo("M");
        assertThat(reset.status()).isEqualTo(ConversationStateTool.Status.RESET);
        assertThat(reset.nextState().selection()).isEqualTo(ConversationSelection.empty());
    }
}
