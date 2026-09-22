package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.wally.customersupport.catalog.application.service.CatalogFact;
import com.wally.customersupport.catalog.application.service.CatalogSearchResult;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationRequest;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptDefinition;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptRegistry;
import com.wally.customersupport.shared.infrastructure.config.AiResponseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BedrockResponseHumanizerTest {

    private BedrockConverseClient converseClient;
    private BedrockResponseHumanizer humanizer;

    @BeforeEach
    void setUp() {
        converseClient = mock(BedrockConverseClient.class);
        PromptRegistry promptRegistry = mock(PromptRegistry.class);
        when(promptRegistry.responsePrompt("conversation-response-v1"))
                .thenReturn(new PromptDefinition(
                        "conversation-response",
                        "conversation-response-v1",
                        "Usa solo los hechos aprobados.",
                        "prompt-hash-v1"));
        humanizer = new BedrockResponseHumanizer(
                converseClient,
                new AiResponseProperties(
                        "conversation-response-v1", 256, BigDecimal.valueOf(0.2), 2_000, 6, 8_000, 4_000),
                promptRegistry);
    }

    @Test
    void humanizesOnlyWhenTheResponsePreservesApprovedCatalogFacts() {
        when(converseClient.complete(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(), anyString(), anyString()))
                .thenReturn("Encontré tu Remera NullPointer: RP-REM-NP-NEG-M, Negro, talle M, "
                        + "18.900,00 ARS y stock disponible: 12.");

        ResponseHumanizationResult result = humanizer.humanize(request(matchedResult()));

        assertThat(result.outcome()).isEqualTo(ResponseHumanizationResult.Outcome.APPLIED);
        assertThat(result.policyId()).isEqualTo(BedrockResponseHumanizer.POLICY_ID);
        assertThat(result.text()).contains("Remera NullPointer", "RP-REM-NP-NEG-M", "18.900,00 ARS", "12");
    }

    @Test
    void fallsBackWhenBedrockIntroducesAnUnapprovedPriceOrStock() {
        when(converseClient.complete(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(), anyString(), anyString()))
                .thenReturn("La Remera NullPointer RP-REM-NP-NEG-M cuesta 99.999,00 ARS y tiene stock: 999.");

        ResponseHumanizationResult result = humanizer.humanize(request(matchedResult()));

        assertThat(result.outcome()).isEqualTo(ResponseHumanizationResult.Outcome.FALLBACK);
        assertThat(result.fallbackReason()).isEqualTo("FACTS_NOT_PRESERVED");
        assertThat(result.text()).contains("18.900,00 ARS", "stock disponible: 12");
        assertThat(result.text()).doesNotContain("99.999", "999");
    }

    @Test
    void fallsBackWhenBedrockReordersCatalogVariantsUsedForOrdinalReferences() {
        CatalogSearchResult resultFacts = new CatalogSearchResult(
                CatalogSearchResult.Status.MATCHED,
                List.of(
                        new CatalogFact(
                                "Buzo Spring Boot", "RP-BUZ-SB-GRI-L", "L", "Gris",
                                new BigDecimal("42900.00"), "ARS", 5),
                        new CatalogFact(
                                "Campera Deploy Friday", "RP-CAM-DF-AZU-M", "M", "Azul",
                                new BigDecimal("67900.00"), "ARS", 4)),
                null,
                CatalogSearchResult.FollowUpKind.NONE,
                "MATCHED");
        when(converseClient.complete(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(), anyString(), anyString()))
                .thenReturn("Campera Deploy Friday RP-CAM-DF-AZU-M, Azul, talle M, 67.900,00 ARS, stock 4. "
                        + "Buzo Spring Boot RP-BUZ-SB-GRI-L, Gris, talle L, 42.900,00 ARS, stock 5.");

        ResponseHumanizationResult result = humanizer.humanize(request(resultFacts));

        assertThat(result.outcome()).isEqualTo(ResponseHumanizationResult.Outcome.FALLBACK);
        assertThat(result.fallbackReason()).isEqualTo("FACTS_NOT_PRESERVED");
        assertThat(result.text().indexOf("RP-BUZ-SB-GRI-L"))
                .isLessThan(result.text().indexOf("RP-CAM-DF-AZU-M"));
    }

    @Test
    void fallsBackToDeterministicFactsWhenBedrockFails() {
        when(converseClient.complete(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("synthetic provider failure"));

        ResponseHumanizationResult result = humanizer.humanize(request(matchedResult()));

        assertThat(result.outcome()).isEqualTo(ResponseHumanizationResult.Outcome.FALLBACK);
        assertThat(result.fallbackReason()).isEqualTo("PROVIDER_ERROR");
        assertThat(result.text()).contains("Remera NullPointer", "18.900,00 ARS", "stock disponible: 12");
    }

    @Test
    void doesNotCallBedrockForInvalidInput() {
        ResponseHumanizationResult result = humanizer.humanize(null);

        assertThat(result.outcome()).isEqualTo(ResponseHumanizationResult.Outcome.FALLBACK);
        assertThat(result.fallbackReason()).isEqualTo("INVALID_INPUT");
        verifyNoInteractions(converseClient);
    }

    @Test
    void selectsExactlyOneHumanizerForEachProvider() {
        PromptRegistry promptRegistry = mock(PromptRegistry.class);
        when(promptRegistry.responsePrompt("conversation-response-v1"))
                .thenReturn(new PromptDefinition(
                        "conversation-response",
                        "conversation-response-v1",
                        "Usa solo los hechos aprobados.",
                        "prompt-hash-v1"));

        new ApplicationContextRunner()
                .withBean(BedrockConverseClient.class, () -> mock(BedrockConverseClient.class))
                .withBean(AiResponseProperties.class, () -> new AiResponseProperties(
                        "conversation-response-v1", 256, BigDecimal.valueOf(0.2), 2_000, 6, 8_000, 4_000))
                .withBean(PromptRegistry.class, () -> promptRegistry)
                .withUserConfiguration(
                        BedrockResponseHumanizer.class,
                        com.wally.customersupport.conversation.application.service
                                .DeterministicResponseHumanizer.class)
                .withPropertyValues("wcs.ai.provider=bedrock")
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            com.wally.customersupport.conversation.application.port.out.ResponseHumanizer.class);
                    assertThat(context.getBean(
                            com.wally.customersupport.conversation.application.port.out.ResponseHumanizer.class))
                            .isInstanceOf(BedrockResponseHumanizer.class);
                });

        new ApplicationContextRunner()
                .withUserConfiguration(
                        BedrockResponseHumanizer.class,
                        com.wally.customersupport.conversation.application.service
                                .DeterministicResponseHumanizer.class)
                .withPropertyValues("wcs.ai.provider=mock")
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            com.wally.customersupport.conversation.application.port.out.ResponseHumanizer.class);
                    assertThat(context.getBean(
                            com.wally.customersupport.conversation.application.port.out.ResponseHumanizer.class))
                            .isInstanceOf(com.wally.customersupport.conversation.application.service
                                    .DeterministicResponseHumanizer.class);
                });
    }

    private static ResponseHumanizationRequest request(CatalogSearchResult result) {
        return new ResponseHumanizationRequest("CATALOG_SEARCH", Channel.TELEGRAM, result);
    }

    private static CatalogSearchResult matchedResult() {
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
}
