package com.wally.customersupport.conversation.application.service;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.wally.customersupport.catalog.application.service.CatalogQueryParser;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionResult;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationSelection;
import com.wally.customersupport.conversation.domain.model.ConversationState;
import com.wally.customersupport.conversation.domain.model.ConversationWorkingMemory;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Projects a bounded, structured selection from a completed turn.
 *
 * <p>The projection is deliberately not used as a source of catalog facts. It
 * only gives the next router a typed starting point for refinements; the
 * catalog service still validates every query against PostgreSQL.</p>
 */
@Service
@Slf4j
public class ConversationSelectionStateService {

    public ConversationState read(ConversationState current) {
        return Objects.requireNonNull(current, "current");
    }

    public ConversationState mergeSelection(
            ConversationState current,
            String selectionField,
            String selectionValue,
            Instant updatedAt) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (selectionField == null || selectionField.isBlank()) {
            throw new IllegalArgumentException("selectionField must not be blank");
        }
        if (selectionValue == null || selectionValue.isBlank()) {
            throw new IllegalArgumentException("selectionValue must not be blank");
        }
        CatalogQuery previous = current.selection().catalogQuery();
        CatalogQuery patch = switch (selectionField) {
            case "name" -> new CatalogQuery(selectionValue, null, null, null);
            case "sku" -> new CatalogQuery(null, selectionValue, null, null);
            case "productType" -> new CatalogQuery(null, null, null, null, selectionValue);
            case "size" -> new CatalogQuery(null, null, selectionValue, null);
            case "color" -> new CatalogQuery(null, null, null, selectionValue);
            case "minPrice" -> new CatalogQuery(null, null, null, null, null,
                    new BigDecimal(selectionValue), null);
            case "maxPrice" -> new CatalogQuery(null, null, null, null, null,
                    null, new BigDecimal(selectionValue));
            default -> throw new IllegalArgumentException("unsupported selectionField: " + selectionField);
        };
        return withSelection(current, previous.merge(patch), updatedAt);
    }

    public ConversationState reset(ConversationState current, Instant updatedAt) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(updatedAt, "updatedAt");
        return new ConversationState(
                current.conversationId(),
                current.actorId(),
                current.recentMessages(),
                updatedAt,
                current.version(),
                current.summary(),
                ConversationSelection.empty());
    }

    public ConversationState update(
            ConversationState current,
            ConversationContext context,
            ConversationExecutionResult result,
            Instant updatedAt) {
        if (current == null || context == null || result == null || updatedAt == null) {
            return current;
        }

        ConversationSelection previous = current.selection();
        boolean generalCatalogRequest = CatalogQueryParser.isGeneralCatalogRequest(context.latestMessage())
                && !CatalogQueryParser.isContextualContinuation(context.latestMessage());
        Optional<CatalogQuery> parsedQuery = generalCatalogRequest
                ? Optional.of(CatalogQuery.empty())
                : CatalogQueryParser.parseConversation(context.recentMessages(), context.latestMessage())
                        .filter(query -> !query.isEmpty());
        CatalogQuery activeQuery = generalCatalogRequest
                ? CatalogQuery.empty()
                : parsedQuery.orElse(previous.catalogQuery());
        String selectedSku = generalCatalogRequest
                ? null
                : parsedQuery.map(CatalogQuery::sku)
                .filter(value -> value != null && !value.isBlank())
                .orElse(previous.selectedVariantSku());

        boolean clearWorkingMemory = result.workingMemory() != null
                && "CLEARED".equals(result.workingMemory().lastCatalogStatus());
        if (clearWorkingMemory) {
            activeQuery = CatalogQuery.empty();
            selectedSku = null;
        }

        ConversationWorkingMemory workingMemory = workingMemory(
                previous.workingMemory(),
                result,
                generalCatalogRequest,
                updatedAt);
        if (workingMemory.hasCatalogObservation() && !workingMemory.hasCandidates()) {
            selectedSku = parsedQuery.map(CatalogQuery::sku)
                    .filter(value -> value != null && !value.isBlank())
                    .orElse(null);
        }
        if (!generalCatalogRequest && workingMemory.focusedSku() != null) {
            selectedSku = workingMemory.focusedSku();
        }

        String memoryResult = clearWorkingMemory
                || (generalCatalogRequest && !workingMemory.hasCandidates())
                        ? "RESET"
                        : workingMemory.hasCatalogObservation() && !workingMemory.hasCandidates()
                                ? "CANDIDATES_CLEARED"
                                : "UPDATED";
        StructuredEventLog.info(log, "CONVERSATION_WORKING_MEMORY_UPDATED", java.util.Map.of(
                "operation", "conversation.working_memory.update",
                "result", memoryResult,
                "correlationId", current.conversationId(),
                "candidateCount", workingMemory.catalogCandidates().size(),
                "focusedSkuPresent", workingMemory.focusedSku() != null,
                "catalogObservation", workingMemory.hasCatalogObservation(),
                "lastCatalogStatus", workingMemory.lastCatalogStatus() == null
                        ? "NONE" : workingMemory.lastCatalogStatus()));

        return new ConversationState(
                current.conversationId(),
                current.actorId(),
                current.recentMessages(),
                updatedAt,
                current.version(),
                current.summary(),
                new ConversationSelection(
                        intentFor(result.useCase(), previous.intent()),
                        actionFor(result.useCase(), previous.action()),
                        activeQuery,
                        selectedSku,
                        result.useCase(),
                        workingMemory));
    }

    private static ConversationState withSelection(
            ConversationState current,
            CatalogQuery query,
            Instant updatedAt) {
        ConversationSelection previous = current.selection();
        return new ConversationState(
                current.conversationId(),
                current.actorId(),
                current.recentMessages(),
                updatedAt,
                current.version(),
                current.summary(),
                new ConversationSelection(
                        previous.intent(),
                        previous.action(),
                        query,
                        query.sku() == null ? previous.selectedVariantSku() : query.sku(),
                        previous.stage(),
                        previous.workingMemory()));
    }

    private static ConversationWorkingMemory workingMemory(
            ConversationWorkingMemory previous,
            ConversationExecutionResult result,
        boolean generalCatalogRequest,
        Instant updatedAt) {
        if (generalCatalogRequest) {
            ConversationWorkingMemory observed = result.workingMemory();
            return new ConversationWorkingMemory(
                    observed.catalogCandidates(),
                    observed.focusedSku(),
                    result.useCase(),
                    observed.lastCatalogStatus(),
                    updatedAt);
        }

        ConversationWorkingMemory observed = result.workingMemory();
        if ("CLEARED".equals(observed.lastCatalogStatus())) {
            return ConversationWorkingMemory.empty();
        }
        if (observed.hasCatalogObservation()) {
            return new ConversationWorkingMemory(
                    observed.catalogCandidates(),
                    observed.focusedSku(),
                    result.useCase(),
                    observed.lastCatalogStatus(),
                    updatedAt);
        }
        return previous.withPendingAction(result.useCase());
    }

    private static ConversationIntent intentFor(String useCase, ConversationIntent previous) {
        if (useCase == null || useCase.isBlank()) {
            return previous;
        }
        try {
            return ConversationIntent.valueOf(useCase);
        } catch (IllegalArgumentException ignored) {
            return previous;
        }
    }

    private static ConversationAction actionFor(String useCase, ConversationAction previous) {
        if (useCase == null || useCase.isBlank()) {
            return previous;
        }
        try {
            return ConversationAction.valueOf(useCase);
        } catch (IllegalArgumentException ignored) {
            return previous;
        }
    }
}
