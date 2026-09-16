package com.wally.customersupport.cart.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.backoffice.application.model.BackofficeOrder;
import com.wally.customersupport.order.application.service.OrderApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderCartCheckoutCreatorTest {

    @Mock
    private OrderApplicationService orderService;

    @Test
    void mapsAllCartItemsAndUsesCartVersionAsIdempotencyBoundary() {
        UUID cartId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID orderId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        BackofficeOrder order = new BackofficeOrder(
                orderId,
                "telegram:actor-hash",
                "PENDING_PAYMENT",
                "ARS",
                new BigDecimal("80700.00"),
                "mock",
                "pref-1",
                "https://pay.invalid/cart-1",
                null,
                null,
                null,
                List.of());
        when(orderService.create(argThat(command -> command.cartId().equals(cartId)
                && command.cartVersion() == 3L
                && command.idempotencyKey().equals("cart-" + cartId + "-v3")
                && command.items().equals(List.of(
                        new OrderApplicationService.RequestedItem("SKU-1", 2),
                        new OrderApplicationService.RequestedItem("SKU-2", 1))))))
                .thenReturn(order);

        var result = new OrderCartCheckoutCreator(orderService).create(
                new com.wally.customersupport.cart.application.port.out.CartCheckoutCreator.CreateCartCheckoutRequest(
                        UUID.randomUUID(),
                        "telegram:actor-hash",
                        cartId,
                        3,
                        List.of(
                                new com.wally.customersupport.cart.application.port.out.CartCheckoutCreator.Item("SKU-1", 2),
                                new com.wally.customersupport.cart.application.port.out.CartCheckoutCreator.Item("SKU-2", 1))));

        assertTrue(result.isPresent());
        assertEquals(orderId, result.get().orderId());
        assertEquals("https://pay.invalid/cart-1", result.get().checkoutUrl());
        verify(orderService).create(argThat(command -> command.cartId().equals(cartId)));
    }
}
