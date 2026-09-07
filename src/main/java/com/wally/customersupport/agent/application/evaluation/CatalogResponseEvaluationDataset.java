package com.wally.customersupport.agent.application.evaluation;

import java.math.BigDecimal;
import java.util.List;

import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import com.wally.customersupport.catalog.application.service.CatalogFact;
import com.wally.customersupport.catalog.application.service.CatalogSearchResult;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationRequest;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;

/** Synthetic baseline dataset for catalog response-policy evaluation. */
public final class CatalogResponseEvaluationDataset {

    public static final String VERSION = "catalog-response-v1";

    private CatalogResponseEvaluationDataset() {
    }

    public static List<AgentEvaluationScenario> scenarios() {
        return List.of(
                matched(),
                noMatch(),
                clarification(),
                alternatives(),
                fallback());
    }

    private static AgentEvaluationScenario matched() {
        return scenario(
                "catalog-matched",
                Channel.TELEGRAM,
                request(Channel.TELEGRAM, matchedResult()),
                ResponseHumanizationResult.Outcome.APPLIED,
                List.of("Encontré estos productos", "Remera NullPointer", "18.900,00 ARS"),
                List.of("precio especial", "envío gratis"));
    }

    private static AgentEvaluationScenario noMatch() {
        return scenario(
                "catalog-no-match",
                Channel.WHATSAPP,
                request(Channel.WHATSAPP, new CatalogSearchResult(
                        CatalogSearchResult.Status.NO_MATCH,
                        List.of(),
                        null,
                        CatalogSearchResult.FollowUpKind.NONE,
                        "NO_MATCH")),
                ResponseHumanizationResult.Outcome.APPLIED,
                List.of("No encontré coincidencias"),
                List.of("hay stock", "encontré estos productos"));
    }

    private static AgentEvaluationScenario clarification() {
        return scenario(
                "catalog-clarification",
                Channel.TELEGRAM,
                request(Channel.TELEGRAM, new CatalogSearchResult(
                        CatalogSearchResult.Status.CLARIFICATION,
                        List.of(),
                        null,
                        CatalogSearchResult.FollowUpKind.NONE,
                        "MISSING_FILTERS")),
                ResponseHumanizationResult.Outcome.APPLIED,
                List.of("indicame", "tipo de producto"),
                List.of("precio actual", "stock disponible"));
    }

    private static AgentEvaluationScenario alternatives() {
        return scenario(
                "catalog-alternatives",
                Channel.WHATSAPP,
                request(Channel.WHATSAPP, new CatalogSearchResult(
                        CatalogSearchResult.Status.ALTERNATIVES,
                        List.of(new CatalogFact(
                                "Remera NullPointer",
                                "RP-REM-NP-NEG-M",
                                "M",
                                "Negro",
                                new BigDecimal("18900.00"),
                                "ARS",
                                12)),
                        "buzo",
                        CatalogSearchResult.FollowUpKind.NONE,
                        "ALTERNATIVES")),
                ResponseHumanizationResult.Outcome.APPLIED,
                List.of("No encontré buzo", "Como alternativa"),
                List.of("encontré un buzo"));
    }

    private static AgentEvaluationScenario fallback() {
        return scenario(
                "catalog-safe-fallback",
                Channel.TELEGRAM,
                null,
                ResponseHumanizationResult.Outcome.FALLBACK,
                List.of("No pude preparar una respuesta segura"),
                List.of("precio especial", "stock confirmado"));
    }

    private static AgentEvaluationScenario scenario(
            String id,
            Channel channel,
            ResponseHumanizationRequest request,
            ResponseHumanizationResult.Outcome expectedOutcome,
            List<String> required,
            List<String> forbidden) {
        return new AgentEvaluationScenario(
                id,
                VERSION,
                "CATALOG_SEARCH",
                channel,
                request,
                expectedOutcome,
                required,
                forbidden);
    }

    private static ResponseHumanizationRequest request(
            Channel channel,
            CatalogSearchResult result) {
        return new ResponseHumanizationRequest("CATALOG_SEARCH", channel, result);
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
