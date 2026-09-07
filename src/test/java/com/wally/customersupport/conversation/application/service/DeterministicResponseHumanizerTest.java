package com.wally.customersupport.conversation.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.wally.customersupport.catalog.application.service.CatalogFact;
import com.wally.customersupport.catalog.application.service.CatalogSearchResult;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationRequest;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import org.junit.jupiter.api.Test;

class DeterministicResponseHumanizerTest {

    private final DeterministicResponseHumanizer humanizer = new DeterministicResponseHumanizer();

    @Test
    void rendersOnlyTheFactsProvidedByTheCatalogResult() {
        ResponseHumanizationResult result = humanizer.humanize(new ResponseHumanizationRequest(
                "CATALOG_SEARCH",
                Channel.TELEGRAM,
                matchedResult()));

        assertThat(result.outcome()).isEqualTo(ResponseHumanizationResult.Outcome.APPLIED);
        assertThat(result.policyId()).isEqualTo(DeterministicResponseHumanizer.POLICY_ID);
        assertThat(result.policyVersion()).isEqualTo(DeterministicResponseHumanizer.POLICY_VERSION);
        assertThat(result.text()).contains("Remera NullPointer", "18.900,00 ARS", "stock disponible: 12");
        assertThat(result.text()).doesNotContain("precio especial", "envío gratis");
    }

    @Test
    void returnsSafeFallbackWhenTheRequestIsInvalid() {
        ResponseHumanizationResult result = humanizer.humanize(null);

        assertThat(result.outcome()).isEqualTo(ResponseHumanizationResult.Outcome.FALLBACK);
        assertThat(result.fallbackReason()).isEqualTo("INVALID_INPUT");
        assertThat(result.text()).doesNotContain("null");
    }

    @Test
    void preservesNoMatchAndClarificationAsPolicyResults() {
        ResponseHumanizationResult noMatch = humanizer.humanize(new ResponseHumanizationRequest(
                "CATALOG_SEARCH",
                Channel.WHATSAPP,
                new CatalogSearchResult(
                        CatalogSearchResult.Status.NO_MATCH,
                        List.of(),
                        null,
                        CatalogSearchResult.FollowUpKind.NONE,
                        "NO_MATCH")));

        assertThat(noMatch.outcome()).isEqualTo(ResponseHumanizationResult.Outcome.APPLIED);
        assertThat(noMatch.text()).contains("No encontré coincidencias");
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
