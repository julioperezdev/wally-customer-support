package com.wally.customersupport.conversation.application.service;

import java.util.Map;
import java.util.Optional;

import com.wally.customersupport.agent.application.service.AgentRuntimeDefinitionResolution;
import com.wally.customersupport.catalog.application.service.CatalogQueryParser;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Composes independent catalog facts and policy knowledge for a single
 * customer turn. It owns no retrieval rules beyond deciding whether both
 * bounded results are needed for this response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class CatalogPolicyCompositionUseCase {

    private final CatalogConversationUseCase catalogConversationUseCase;
    private final ConversationSupportUseCase supportUseCase;

    public Optional<ConversationRenderedResponse> compose(
            ConversationContext context,
            ConversationIntentDecision decision,
            AgentRuntimeDefinitionResolution definition) {
        if (context == null
                || CatalogQueryParser.isPurchaseRequest(context.latestMessage())
                || !CatalogQueryParser.isShippingQuestion(context.latestMessage())) {
            return Optional.empty();
        }
        if (resolveCatalogQuery(context, decision).isEmpty()) {
            return Optional.empty();
        }

        ConversationRenderedResponse catalogResponse = catalogConversationUseCase
                .searchResponse(context, decision, definition)
                .orElseGet(catalogConversationUseCase::lowConfidenceResponse);
        String shippingReply = supportUseCase.policy("shipping");
        StructuredEventLog.info(log, "INTENT_COMPOSED", Map.of(
                "primaryIntent", decision == null ? "UNKNOWN" : decision.intent().name(),
                "secondaryIntent", "POLICY_QUERY",
                "components", "CATALOG_SEARCH+SHIPPING",
                "result", "COMPOSED"));
        return Optional.of(new ConversationRenderedResponse(
                catalogResponse.text() + "\n\n" + shippingReply,
                catalogResponse.mediaReference()));
    }

    private Optional<CatalogQuery> resolveCatalogQuery(
            ConversationContext context,
            ConversationIntentDecision decision) {
        if (decision != null && decision.catalogQuery() != null && !decision.catalogQuery().isEmpty()) {
            return Optional.of(decision.catalogQuery());
        }
        return CatalogQueryParser.parseConversation(context.recentMessages(), context.latestMessage())
                .filter(query -> !query.isEmpty())
                .or(() -> CatalogQueryParser.parse(context.latestMessage())
                        .filter(query -> !query.isEmpty()));
    }
}
