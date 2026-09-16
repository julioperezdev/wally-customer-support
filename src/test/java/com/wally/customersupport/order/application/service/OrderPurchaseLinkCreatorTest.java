package com.wally.customersupport.order.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.backoffice.application.model.BackofficeOrder;
import com.wally.customersupport.conversation.application.port.out.PurchaseLinkCreator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderPurchaseLinkCreatorTest {

    @Mock
    private OrderApplicationService orderService;

    @Test
    void mapsTheExistingOrderContractToTheConversationCheckoutContract() {
        UUID orderId = UUID.randomUUID();
        when(orderService.create(any())).thenReturn(new BackofficeOrder(
                orderId,
                "telegram:customer-1",
                "PENDING_PAYMENT",
                "ARS",
                new BigDecimal("37800.00"),
                "mock",
                "preference-1",
                "https://sandbox.example.invalid/pay/order-1",
                null,
                Instant.parse("2026-09-16T12:00:00Z"),
                Instant.parse("2026-09-16T12:00:00Z"),
                List.of(new BackofficeOrder.Item(
                        "RP-REM-NP-NEG-M",
                        "Remera NullPointer",
                        2,
                        new BigDecimal("18900.00"),
                        "ARS",
                        new BigDecimal("37800.00")))));

        OrderPurchaseLinkCreator creator = new OrderPurchaseLinkCreator(orderService);
        var result = creator.create(new PurchaseLinkCreator.CreatePurchaseLinkRequest(
                UUID.randomUUID(), "telegram:customer-1", "RP-REM-NP-NEG-M", 2, "purchase-key"));

        assertTrue(result.isPresent());
        assertEquals(orderId, result.get().orderId());
        assertEquals("Remera NullPointer", result.get().productName());
        assertEquals("RP-REM-NP-NEG-M", result.get().sku());
        assertEquals(2, result.get().quantity());
        assertEquals(new BigDecimal("37800.00"), result.get().total());
        assertEquals("https://sandbox.example.invalid/pay/order-1", result.get().checkoutUrl());

        ArgumentCaptor<OrderApplicationService.CreateOrderCommand> command =
                ArgumentCaptor.forClass(OrderApplicationService.CreateOrderCommand.class);
        verify(orderService).create(command.capture());
        assertEquals("telegram:customer-1", command.getValue().customerReference());
        assertEquals("RP-REM-NP-NEG-M", command.getValue().items().getFirst().sku());
        assertEquals(2, command.getValue().items().getFirst().quantity());
        assertEquals("purchase-key", command.getValue().idempotencyKey());
    }

    @Test
    void doesNotExposeAConversationCheckoutWhenTheOrderHasNoPaymentUrl() {
        when(orderService.create(any())).thenReturn(new BackofficeOrder(
                UUID.randomUUID(),
                "telegram:customer-1",
                "PENDING_PAYMENT",
                "ARS",
                new BigDecimal("18900.00"),
                "mercadopago",
                null,
                null,
                null,
                Instant.parse("2026-09-16T12:00:00Z"),
                Instant.parse("2026-09-16T12:00:00Z"),
                List.of(new BackofficeOrder.Item(
                        "RP-REM-NP-NEG-M",
                        "Remera NullPointer",
                        1,
                        new BigDecimal("18900.00"),
                        "ARS",
                        new BigDecimal("18900.00")))));

        OrderPurchaseLinkCreator creator = new OrderPurchaseLinkCreator(orderService);

        assertTrue(creator.create(new PurchaseLinkCreator.CreatePurchaseLinkRequest(
                UUID.randomUUID(), "telegram:customer-1", "RP-REM-NP-NEG-M", 1, "purchase-key"))
                .isEmpty());
    }
}
