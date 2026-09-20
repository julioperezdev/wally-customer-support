package com.wally.customersupport.conversation.application.service;

import java.util.Optional;

import com.wally.customersupport.agent.application.service.CatalogSpecialistExecutionRequest;
import com.wally.customersupport.agent.application.service.CatalogSpecialistExecutionResult;
import com.wally.customersupport.agent.application.service.CatalogSpecialistExecutor;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinitionResolution;
import com.wally.customersupport.catalog.application.service.CatalogConversationService;
import com.wally.customersupport.catalog.application.service.CatalogResponseFormatter;
import com.wally.customersupport.catalog.application.service.CatalogSearchResult;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.application.port.out.ResponseHumanizer;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationRequest;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Owns the catalog conversation boundary.
 *
 * <p>It chooses the active bounded specialist when available, falls back to
 * the deterministic catalog service, and only then applies presentation. No
 * caller needs to know how catalog facts were obtained.</p>
 */
@Service
public class CatalogConversationUseCase {

    private static final String LOW_CONFIDENCE = "No estoy seguro de haber entendido tu consulta. "
            + "Podés preguntarme por productos, stock, horarios, envíos o cambios.";

    private final CatalogConversationService catalogConversationService;
    private final CatalogSpecialistExecutor catalogSpecialistExecutor;
    private final ResponseHumanizer responseHumanizer;
    private final ConversationExecutionTelemetry telemetry;

    @Autowired
    public CatalogConversationUseCase(
            CatalogConversationService catalogConversationService,
            CatalogSpecialistExecutor catalogSpecialistExecutor,
            ResponseHumanizer responseHumanizer,
            ConversationExecutionTelemetry telemetry) {
        this.catalogConversationService = catalogConversationService;
        this.catalogSpecialistExecutor = catalogSpecialistExecutor;
        this.responseHumanizer = responseHumanizer;
        this.telemetry = telemetry;
    }

    public Optional<ConversationRenderedResponse> searchResponse(
            ConversationContext context,
            ConversationIntentDecision decision,
            AgentRuntimeDefinitionResolution definition) {
        return searchFacts(context, decision, definition)
                .map(result -> humanize(context, result));
    }

    public Optional<CatalogSearchResult> searchFacts(
            ConversationContext context,
            ConversationIntentDecision decision,
            AgentRuntimeDefinitionResolution definition) {
        return searchFacts(
                context,
                decision == null ? null : decision.catalogQuery(),
                decision,
                definition);
    }

    public Optional<CatalogSearchResult> searchFacts(
            ConversationContext context,
            CatalogQuery query) {
        return searchFacts(context, query, null, null);
    }

    private Optional<CatalogSearchResult> searchFacts(
            ConversationContext context,
            CatalogQuery query,
            ConversationIntentDecision decision,
            AgentRuntimeDefinitionResolution definition) {
        if (definition != null && definition.isActive()) {
            CatalogSpecialistExecutionResult specialistResult = catalogSpecialistExecutor.execute(
                    new CatalogSpecialistExecutionRequest(
                            definition.definition(),
                            query,
                            context.recentMessages(),
                            context.latestMessage()));
            if (specialistResult.executed()) {
                telemetry.logCatalogSearchOutcome(
                        context, decision, specialistResult.result(),
                        "agent-specialist", specialistResult.durationMs());
                return Optional.of(specialistResult.result());
            }
        }

        long searchStartedAt = System.nanoTime();
        Optional<CatalogSearchResult> result = catalogConversationService.search(
                query,
                context.recentMessages(),
                context.latestMessage());
        result.ifPresentOrElse(
                catalogResult -> telemetry.logCatalogSearchOutcome(
                        context, decision, catalogResult,
                        "deterministic-catalog", telemetry.elapsedMillis(searchStartedAt)),
                () -> telemetry.logCatalogSearchOutcome(
                        context, decision, null,
                        "deterministic-catalog", telemetry.elapsedMillis(searchStartedAt)));
        return result;
    }

    private ConversationRenderedResponse humanize(
            ConversationContext context,
            CatalogSearchResult result) {
        if (context == null || context.channel() == null) {
            return ConversationRenderedResponse.catalog(CatalogResponseFormatter.render(result), result);
        }
        ResponseHumanizationResult humanized = responseHumanizer.humanize(
                new ResponseHumanizationRequest("CATALOG_SEARCH", context.channel(), result));
        if (humanized == null || humanized.text() == null || humanized.text().isBlank()) {
            return ConversationRenderedResponse.catalog(CatalogResponseFormatter.render(result), result);
        }
        return ConversationRenderedResponse.catalog(humanized.text(), result);
    }

    public ConversationRenderedResponse lowConfidenceResponse() {
        return ConversationRenderedResponse.text(LOW_CONFIDENCE);
    }
}
