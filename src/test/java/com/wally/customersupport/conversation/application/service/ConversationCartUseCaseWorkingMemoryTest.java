package com.wally.customersupport.conversation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.cart.application.port.in.CartConversationHandler;
import com.wally.customersupport.cart.application.service.CartCommandParser;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.CatalogCandidateReference;
import com.wally.customersupport.conversation.domain.model.CatalogObservationStatus;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationSelection;
import com.wally.customersupport.conversation.domain.model.ConversationWorkingMemory;
import org.junit.jupiter.api.Test;

class ConversationCartUseCaseWorkingMemoryTest {

    @Test
    void sendsResolvedSkuToCartHandlerForAnaphoricAddRequest() {
        CartConversationHandler handler = mock(CartConversationHandler.class);
        when(handler.recognizes("Bueno, prosigamos con la campera, agregala al carrito")).thenReturn(true);
        when(handler.handle(any(ConversationContext.class), any(CartCommandParser.Command.class)))
                .thenReturn(Optional.of(new CartConversationHandler.Response("added")));
        ConversationCartUseCase useCase = new ConversationCartUseCase(
                handler,
                new ConversationExecutionPlanFactory());
        var memory = ConversationWorkingMemory.catalogObservation(
                List.of(new CatalogCandidateReference(
                        "Campera Deploy Friday", "RP-CAM-DF-AZU-M", "M", "Azul")),
                CatalogObservationStatus.MATCHED,
                Instant.now());
        ConversationSelection selection = new ConversationSelection(
                ConversationIntent.CATALOG_SEARCH,
                ConversationAction.CATALOG_SEARCH,
                CatalogQuery.empty(),
                memory.focusedSku(),
                "CATALOG_SEARCH",
                memory);
        ConversationContext context = new ConversationContext(
                UUID.randomUUID(), "actor", "Bueno, prosigamos con la campera, agregala al carrito",
                List.of(), List.of(), null, List.of(), Channel.TELEGRAM, selection);

        useCase.handleBeforeRouting(context);

        var captor = org.mockito.ArgumentCaptor.forClass(CartCommandParser.Command.class);
        verify(handler).handle(eq(context), captor.capture());
        assertEquals(CartCommandParser.Action.ADD, captor.getValue().action());
        assertEquals("RP-CAM-DF-AZU-M", captor.getValue().query().sku());
    }
}
