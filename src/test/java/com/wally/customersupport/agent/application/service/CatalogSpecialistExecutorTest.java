package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.catalog.application.service.CatalogConversationService;
import com.wally.customersupport.catalog.application.service.CatalogFact;
import com.wally.customersupport.catalog.application.service.CatalogSearchResult;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogSpecialistExecutorTest {

    @Mock
    private CatalogConversationService catalogConversationService;

    @Test
    void delegatesActiveDefinitionToTheDeterministicCatalogCapability() {
        CatalogSpecialistExecutor executor = new CatalogSpecialistExecutor(catalogConversationService);
        CatalogSpecialistExecutionRequest request = request(definition(Set.of("catalog.search")));
        when(catalogConversationService.search(
                request.catalogQuery(), request.recentMessages(), request.latestMessage()))
                .thenReturn(java.util.Optional.of(structuredResult()));

        CatalogSpecialistExecutionResult result = executor.execute(request);

        assertThat(result.status()).isEqualTo(CatalogSpecialistExecutionResult.Status.EXECUTED);
        assertThat(result.result().facts().getFirst().sku()).isEqualTo("RP-REM-NP-NEG-M");
        verify(catalogConversationService).search(
                request.catalogQuery(), request.recentMessages(), request.latestMessage());
    }

    @Test
    void refusesExecutionWhenTheDefinitionDoesNotAuthorizeTheCatalogTool() {
        CatalogSpecialistExecutor executor = new CatalogSpecialistExecutor(catalogConversationService);

        CatalogSpecialistExecutionResult result = executor.execute(
                request(definition(Set.of())));

        assertThat(result.status()).isEqualTo(CatalogSpecialistExecutionResult.Status.FALLBACK);
        assertThat(result.reason()).isEqualTo("TOOL_NOT_ALLOWED");
        verify(catalogConversationService, never()).search(any(CatalogQuery.class), any(), any());
    }

    @Test
    void convertsAnEmptySpecialistResponseToFallback() {
        CatalogSpecialistExecutor executor = new CatalogSpecialistExecutor(catalogConversationService);
        CatalogSpecialistExecutionRequest request = request(definition(Set.of("catalog.search")));
        when(catalogConversationService.search(
                request.catalogQuery(), request.recentMessages(), request.latestMessage()))
                .thenReturn(java.util.Optional.empty());

        CatalogSpecialistExecutionResult result = executor.execute(request);

        assertThat(result.status()).isEqualTo(CatalogSpecialistExecutionResult.Status.FALLBACK);
        assertThat(result.reason()).isEqualTo("NO_RESPONSE");
    }

    private static CatalogSearchResult structuredResult() {
        return new CatalogSearchResult(
                CatalogSearchResult.Status.MATCHED,
                List.of(new CatalogFact(
                        "Remera NullPointer",
                        "RP-REM-NP-NEG-M",
                        "M",
                        "Negro",
                        new BigDecimal("18900.00"),
                        "ARS",
                        12)),
                null,
                CatalogSearchResult.FollowUpKind.NONE,
                "MATCHED");
    }

    private static CatalogSpecialistExecutionRequest request(AgentRuntimeDefinition definition) {
        return new CatalogSpecialistExecutionRequest(
                definition,
                new CatalogQuery("nullpointer", null, "M", "negro"),
                List.of("Busco una remera negra talle M"),
                "¿Está disponible?");
    }

    private static AgentRuntimeDefinition definition(Set<String> allowedTools) {
        return new AgentRuntimeDefinition(
                "catalog-specialist",
                2,
                "Catalog specialist",
                "Search products using deterministic catalog tools",
                "bedrock",
                "openai.gpt-oss-20b-1:0",
                AgentInferenceParameters.deterministic(),
                "system-v1",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "catalog-input-v1",
                "catalog-output-v1",
                allowedTools,
                Set.of(),
                "conversation-summary-v1",
                "grounded-customer-support-v1",
                Duration.ofSeconds(10),
                2,
                2_000,
                1_000,
                BigDecimal.valueOf(0.05),
                "safe-fallback",
                "catalog-eval-v1");
    }
}
