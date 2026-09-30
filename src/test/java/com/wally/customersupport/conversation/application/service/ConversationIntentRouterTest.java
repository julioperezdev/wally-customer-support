package com.wally.customersupport.conversation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.application.port.out.ConversationIntentClassifier;
import com.wally.customersupport.conversation.application.port.out.TypeSafeUseCaseSelector;
import com.wally.customersupport.conversation.application.port.out.TypeSafeUseCaseSelector.TypeSafeSelection;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.shared.infrastructure.config.ConversationRoutingProperties;
import org.junit.jupiter.api.Test;

class ConversationIntentRouterTest {

    private static final ConversationContext CONTEXT = new ConversationContext(
            null, "synthetic-customer", "quiero agregar un buzo", List.of(), List.of());

    @Test
    void defaultsToExistingClassifierWithoutCallingTypeSafe() {
        ConversationIntentClassifier classifier = mock(ConversationIntentClassifier.class);
        TypeSafeUseCaseSelector selector = mock(TypeSafeUseCaseSelector.class);
        var baseline = decision(ConversationAction.ADD_TO_CART, ConversationIntent.CATALOG_SEARCH);
        when(classifier.classify(CONTEXT)).thenReturn(baseline);

        var actual = new ConversationIntentRouter(classifier, selector, ConversationRoutingProperties.defaults())
                .classify(CONTEXT);

        assertThat(actual).isSameAs(baseline);
        verify(selector, never()).select(CONTEXT);
    }

    @Test
    void shadowModeReturnsBaselineEvenWhenTypeSafeSuggestsDifferentAction() {
        ConversationIntentClassifier classifier = mock(ConversationIntentClassifier.class);
        TypeSafeUseCaseSelector selector = mock(TypeSafeUseCaseSelector.class);
        var baseline = decision(ConversationAction.CATALOG_SEARCH, ConversationIntent.CATALOG_SEARCH);
        when(classifier.classify(CONTEXT)).thenReturn(baseline);
        when(selector.select(CONTEXT)).thenReturn(selection(ConversationAction.ADD_TO_CART, 0.98));

        var actual = new ConversationIntentRouter(classifier, selector, properties("shadow", 0.65))
                .classify(CONTEXT);

        assertThat(actual).isSameAs(baseline);
        verify(selector).select(CONTEXT);
    }

    @Test
    void activeModeUsesTypeSafeActionAndOnlyUsesBaselineForMatchingEntityEnrichment() {
        ConversationIntentClassifier classifier = mock(ConversationIntentClassifier.class);
        TypeSafeUseCaseSelector selector = mock(TypeSafeUseCaseSelector.class);
        var enriched = new ConversationIntentDecision(
                ConversationIntent.CATALOG_SEARCH,
                ConversationAction.ADD_TO_CART,
                0.91,
                new CatalogQuery("buzo", null, "M", null),
                null,
                2,
                List.of());
        when(classifier.classify(CONTEXT)).thenReturn(enriched);
        when(selector.select(CONTEXT)).thenReturn(selection(ConversationAction.ADD_TO_CART, 0.94));

        var actual = new ConversationIntentRouter(classifier, selector, properties("active", 0.65))
                .classify(CONTEXT);

        assertThat(actual.action()).isEqualTo(ConversationAction.ADD_TO_CART);
        assertThat(actual.intent()).isEqualTo(ConversationIntent.CATALOG_SEARCH);
        assertThat(actual.catalogQuery()).isEqualTo(new CatalogQuery("buzo", null, "M", null));
        assertThat(actual.quantity()).isEqualTo(2);
        assertThat(actual.confidence()).isEqualTo(0.94);
    }

    @Test
    void mismatchedBaselineActionCannotEnrichTypeSafeSelection() {
        ConversationIntentClassifier classifier = mock(ConversationIntentClassifier.class);
        TypeSafeUseCaseSelector selector = mock(TypeSafeUseCaseSelector.class);
        when(classifier.classify(CONTEXT)).thenReturn(
                decision(ConversationAction.CATALOG_SEARCH, ConversationIntent.CATALOG_SEARCH));
        when(selector.select(CONTEXT)).thenReturn(selection(ConversationAction.ADD_TO_CART, 0.94));

        var actual = new ConversationIntentRouter(classifier, selector, properties("active", 0.65))
                .classify(CONTEXT);

        assertThat(actual.action()).isEqualTo(ConversationAction.ADD_TO_CART);
        assertThat(actual.catalogQuery()).isNull();
        assertThat(actual.quantity()).isEqualTo(1);
    }

    @Test
    void lowConfidenceAndProviderFailuresFallBackToBaseline() {
        ConversationIntentClassifier classifier = mock(ConversationIntentClassifier.class);
        TypeSafeUseCaseSelector selector = mock(TypeSafeUseCaseSelector.class);
        var baseline = decision(ConversationAction.CATALOG_SEARCH, ConversationIntent.CATALOG_SEARCH);
        when(classifier.classify(CONTEXT)).thenReturn(baseline);
        when(selector.select(CONTEXT))
                .thenReturn(selection(ConversationAction.ADD_TO_CART, 0.64))
                .thenReturn(TypeSafeSelection.failure("TRANSPORT_ERROR"));
        var router = new ConversationIntentRouter(classifier, selector, properties("active", 0.65));

        assertThat(router.classify(CONTEXT)).isSameAs(baseline);
        assertThat(router.classify(CONTEXT)).isSameAs(baseline);
    }

    @Test
    void checkoutAndCartActionsKeepTheExistingIntentContract() {
        ConversationIntentClassifier classifier = mock(ConversationIntentClassifier.class);
        TypeSafeUseCaseSelector selector = mock(TypeSafeUseCaseSelector.class);
        when(selector.select(CONTEXT)).thenReturn(selection(ConversationAction.CANCEL_CHECKOUT, 0.99));

        var actual = new ConversationIntentRouter(classifier, selector, properties("active", 0.65))
                .classify(CONTEXT);

        assertThat(actual.intent()).isEqualTo(ConversationIntent.PURCHASE_LINK);
        assertThat(actual.action()).isEqualTo(ConversationAction.CANCEL_CHECKOUT);
        verify(classifier).classify(CONTEXT);
    }

    private static ConversationRoutingProperties properties(String mode, double minimumConfidence) {
        return new ConversationRoutingProperties(
                "typesafe", mode, minimumConfidence, ConversationRoutingProperties.TypeSafe.defaults());
    }

    private static TypeSafeSelection selection(ConversationAction action, double confidence) {
        return new TypeSafeSelection(action, confidence, "jev-1.13.0", 20, 3, null);
    }

    private static ConversationIntentDecision decision(ConversationAction action, ConversationIntent intent) {
        return new ConversationIntentDecision(intent, action, 0.90, null, null, 1, List.of());
    }
}
