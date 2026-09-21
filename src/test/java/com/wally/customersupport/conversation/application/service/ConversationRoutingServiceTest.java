package com.wally.customersupport.conversation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.application.port.out.ConversationIntentClassifier;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.conversation.domain.model.ConversationSelection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationRoutingServiceTest {

    @Mock
    private ConversationIntentClassifier classifier;

    private ConversationRoutingService router;

    @BeforeEach
    void setUp() {
        router = new ConversationRoutingService(
                new ConversationIntentRouter(classifier),
                new ConversationDecisionReconciler());
    }

    @Test
    void treatsCategoryAsProductTypeWhenModelReturnsItAsAName() {
        ConversationContext context = context("Quiero un buzo", List.of("Quiero un buzo"),
                ConversationSelection.empty());
        when(classifier.classify(context)).thenReturn(new ConversationIntentDecision(
                ConversationIntent.PURCHASE_LINK,
                0.91,
                new CatalogQuery("buzo", null, null, null),
                null));

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(ConversationIntent.CATALOG_SEARCH, result.decision().intent());
        assertEquals(ConversationAction.CATALOG_SEARCH, result.decision().action());
        assertEquals("buzo", result.decision().catalogQuery().productType());
        assertEquals(null, result.decision().catalogQuery().name());
    }

    @Test
    void appliesColorRefinementToThePersistedSelection() {
        ConversationSelection selection = selection(
                new CatalogQuery(null, null, null, null, "buzo"),
                ConversationIntent.CATALOG_SEARCH);
        ConversationContext context = context("que sea negro", List.of("Quiero un buzo"), selection);
        when(classifier.classify(context)).thenReturn(ConversationIntentDecision.unknown());

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(ConversationIntent.CATALOG_SEARCH, result.decision().intent());
        assertEquals("buzo", result.decision().catalogQuery().productType());
        assertEquals("negro", result.decision().catalogQuery().color());
    }

    @Test
    void appliesSizeRefinementWithoutLosingTheActiveProduct() {
        ConversationSelection selection = selection(
                new CatalogQuery("nullpointer", null, null, "negro", "remera"),
                ConversationIntent.CATALOG_SEARCH);
        ConversationContext context = context("la M", List.of("Busco una remera negra"), selection);
        when(classifier.classify(context)).thenReturn(new ConversationIntentDecision(
                ConversationIntent.GENERAL_SUPPORT, 0.80, null, null));

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(ConversationIntent.CATALOG_SEARCH, result.decision().intent());
        assertEquals("nullpointer", result.decision().catalogQuery().name());
        assertEquals("remera", result.decision().catalogQuery().productType());
        assertEquals("negro", result.decision().catalogQuery().color());
        assertEquals("m", result.decision().catalogQuery().size());
    }

    @Test
    void treatsSizeOnlyLanguageAsAFilterInsteadOfAProductName() {
        ConversationContext context = context("Soy talle M", List.of("Que vendes"),
                ConversationSelection.empty());
        when(classifier.classify(context)).thenReturn(new ConversationIntentDecision(
                ConversationIntent.CATALOG_SEARCH,
                0.92,
                new CatalogQuery("soy", null, "m", null, null),
                null));

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(ConversationIntent.CATALOG_SEARCH, result.decision().intent());
        assertEquals("m", result.decision().catalogQuery().size());
        assertEquals(null, result.decision().catalogQuery().name());
    }

    @Test
    void generalCatalogQuestionClearsStaleSelectionFilters() {
        ConversationSelection selection = selection(
                new CatalogQuery("nullpointer", null, "m", "negro", "remera"),
                ConversationIntent.CATALOG_SEARCH);
        ConversationContext context = context("Tenes ropa",
                List.of("Busco una remera negra talle M"), selection);
        when(classifier.classify(context)).thenReturn(new ConversationIntentDecision(
                ConversationIntent.CATALOG_SEARCH,
                0.96,
                new CatalogQuery("ropa", null, "m", "negro", "remera"),
                null));

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertTrue(result.decision().catalogQuery().isEmpty());
    }

    @Test
    void preservesContextualSizeWhenCustomerSelectsAProductCategory() {
        ConversationContext context = context("Quiero una remera", List.of("Soy talle M"),
                ConversationSelection.empty());
        when(classifier.classify(context)).thenReturn(new ConversationIntentDecision(
                ConversationIntent.CATALOG_SEARCH,
                0.95,
                new CatalogQuery("remera", null, null, null, "remera"),
                null));

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals("remera", result.decision().catalogQuery().productType());
        assertEquals("m", result.decision().catalogQuery().size());
        assertEquals(null, result.decision().catalogQuery().name());
    }

    @Test
    void newProductCategoryDoesNotInheritFiltersFromThePreviousCategory() {
        ConversationSelection selection = selection(
                new CatalogQuery("nullpointer", "RP-REM-NP-NEG-M", "M", "negro", "remera"),
                ConversationIntent.CATALOG_SEARCH);
        ConversationContext context = context(
                "Quiero un buzo",
                List.of("Busco una remera negra talle M"),
                selection);
        when(classifier.classify(context)).thenReturn(new ConversationIntentDecision(
                ConversationIntent.CATALOG_SEARCH,
                0.94,
                new CatalogQuery(null, null, null, null, "buzo"),
                null));

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals("buzo", result.decision().catalogQuery().productType());
        assertEquals(null, result.decision().catalogQuery().color());
        assertEquals(null, result.decision().catalogQuery().size());
    }

    @Test
    void explicitPurchaseWinsAndUsesTheActiveSelection() {
        ConversationSelection selection = selection(
                new CatalogQuery("nullpointer", "RP-REM-NP-NEG-M", "M", "negro", "remera"),
                ConversationIntent.CATALOG_SEARCH);
        ConversationContext context = context("Quiero comprarla", List.of(), selection);
        when(classifier.classify(context)).thenReturn(new ConversationIntentDecision(
                ConversationIntent.GENERAL_SUPPORT, 0.80, null, null));

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(ConversationIntent.PURCHASE_LINK, result.decision().intent());
        assertEquals(ConversationAction.PURCHASE_LINK, result.decision().action());
        assertEquals("RP-REM-NP-NEG-M", result.decision().catalogQuery().sku());
    }

    @Test
    void unsupportedCategoryIsStillAValidatedCatalogSearch() {
        ConversationContext context = context("¿Tienen zapatillas?", List.of(), ConversationSelection.empty());
        when(classifier.classify(context)).thenReturn(new ConversationIntentDecision(
                ConversationIntent.GENERAL_SUPPORT, 0.80, null, null));

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(ConversationIntent.CATALOG_SEARCH, result.decision().intent());
        assertEquals("zapatillas", result.decision().catalogQuery().name());
    }

    @Test
    void keepsCartActionAndModelProductNameWhileEnrichingTurnFilters() {
        ConversationContext context = context(
                "Sumame dos de esos, el negro talle XL",
                List.of("Busco un buzo Spring Boot"),
                ConversationSelection.empty());
        when(classifier.classify(context)).thenReturn(new ConversationIntentDecision(
                ConversationIntent.CATALOG_SEARCH,
                ConversationAction.ADD_TO_CART,
                0.96,
                new CatalogQuery("spring boot", null, null, null),
                null,
                2,
                List.of()));

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(ConversationAction.ADD_TO_CART, result.decision().action());
        assertEquals("spring boot", result.decision().catalogQuery().name());
        assertEquals("buzo", result.decision().catalogQuery().productType());
        assertEquals("negro", result.decision().catalogQuery().color());
        assertEquals("xl", result.decision().catalogQuery().size());
        assertEquals(2, result.decision().quantity());
    }

    @Test
    void keepsUnknownRouteWhenNoSafeEntityCanBeResolved() {
        ConversationContext context = context("asdf qwer", List.of(), ConversationSelection.empty());
        when(classifier.classify(context)).thenReturn(ConversationIntentDecision.unknown());

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(ConversationIntent.UNKNOWN, result.decision().intent());
        assertEquals("SAFE_FALLBACK", result.strategy());
        assertTrue(result.resolvedFields().isEmpty());
    }

    @Test
    void preservesLowConfidenceCatalogProposalWithoutCatalogEvidence() {
        ConversationContext context = context("consulta", List.of(), ConversationSelection.empty());
        ConversationIntentDecision proposal = new ConversationIntentDecision(
                ConversationIntent.CATALOG_SEARCH,
                0.40,
                null,
                null);
        when(classifier.classify(context)).thenReturn(proposal);

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(proposal, result.decision());
        assertEquals("MODEL_PROPOSAL", result.strategy());
        assertEquals(0.40, result.decision().confidence());
    }

    @Test
    void routesAnExplicitCatalogMessageWhenTheModelReturnsUnknown() {
        ConversationContext context = context("Quiero una remera", List.of(), ConversationSelection.empty());
        when(classifier.classify(context)).thenReturn(ConversationIntentDecision.unknown());

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(ConversationIntent.CATALOG_SEARCH, result.decision().intent());
        assertEquals(ConversationAction.CATALOG_SEARCH, result.decision().action());
        assertEquals("remera", result.decision().catalogQuery().productType());
        assertEquals("DETERMINISTIC_MESSAGE_SIGNAL", result.strategy());
    }

    @Test
    void routesAProductAvailabilityQuestionWhenTheModelReturnsUnknown() {
        ConversationContext context = context("Tienes un buzo", List.of(), ConversationSelection.empty());
        when(classifier.classify(context)).thenReturn(ConversationIntentDecision.unknown());

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(ConversationIntent.CATALOG_SEARCH, result.decision().intent());
        assertEquals("buzo", result.decision().catalogQuery().productType());
    }

    @Test
    void routesAStandaloneSizeRefinementWhenTheModelReturnsUnknown() {
        ConversationContext context = context("Soy talle m", List.of("Que vendes"), ConversationSelection.empty());
        when(classifier.classify(context)).thenReturn(ConversationIntentDecision.unknown());

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(ConversationIntent.CATALOG_SEARCH, result.decision().intent());
        assertEquals("m", result.decision().catalogQuery().size());
    }

    @Test
    void routesAConversationalSizeMessageAsCatalogContextWhenTheModelReturnsUnknown() {
        ConversationContext context = context(
                "Soy talle mediano",
                List.of("Quiero una remera"),
                ConversationSelection.empty());
        when(classifier.classify(context)).thenReturn(ConversationIntentDecision.unknown());

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(ConversationIntent.CATALOG_SEARCH, result.decision().intent());
        assertEquals("remera", result.decision().catalogQuery().productType());
        assertEquals("m", result.decision().catalogQuery().size());
    }

    @Test
    void routesGeneralSupportWhenTheModelReturnsUnknown() {
        ConversationContext context = context("¿Dónde están ubicados?", List.of(), ConversationSelection.empty());
        when(classifier.classify(context)).thenReturn(ConversationIntentDecision.unknown());

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(ConversationIntent.GENERAL_SUPPORT, result.decision().intent());
        assertEquals(ConversationAction.GENERAL_SUPPORT, result.decision().action());
        assertEquals(0.99, result.decision().confidence());
    }

    @Test
    void routesBusinessHoursWhenTheModelReturnsUnknown() {
        ConversationContext context = context("¿A qué hora abren el sábado?", List.of(), ConversationSelection.empty());
        when(classifier.classify(context)).thenReturn(ConversationIntentDecision.unknown());

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(ConversationIntent.BUSINESS_HOURS, result.decision().intent());
        assertEquals(ConversationAction.BUSINESS_HOURS, result.decision().action());
    }

    @Test
    void routesShippingPolicyWhenTheModelReturnsUnknown() {
        ConversationContext context = context("¿Cómo funcionan los envíos?", List.of(), ConversationSelection.empty());
        when(classifier.classify(context)).thenReturn(ConversationIntentDecision.unknown());

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(ConversationIntent.POLICY_QUERY, result.decision().intent());
        assertEquals("shipping", result.decision().policyKey());
    }

    @Test
    void fillsMissingPolicyKeyFromAnExplicitMessageSignal() {
        ConversationContext context = context("¿Cómo funcionan los envíos?", List.of(), ConversationSelection.empty());
        when(classifier.classify(context)).thenReturn(new ConversationIntentDecision(
                ConversationIntent.POLICY_QUERY,
                ConversationAction.POLICY_QUERY,
                0.90,
                null,
                null,
                1,
                List.of()));

        ConversationRoutingService.RoutingResult result = router.route(context);

        assertEquals(ConversationIntent.POLICY_QUERY, result.decision().intent());
        assertEquals("shipping", result.decision().policyKey());
        assertTrue(result.normalized());
    }

    private static ConversationContext context(
            String latest,
            List<String> recentMessages,
            ConversationSelection selection) {
        return new ConversationContext(
                UUID.randomUUID(),
                "customer-1",
                latest,
                recentMessages,
                List.of(),
                null,
                List.of(),
                Channel.TELEGRAM,
                selection);
    }

    private static ConversationSelection selection(CatalogQuery query, ConversationIntent intent) {
        return new ConversationSelection(
                intent,
                ConversationAction.CATALOG_SEARCH,
                query,
                query.sku(),
                "CATALOG_SEARCH");
    }
}
