package com.wally.customersupport.cart.application.service;

import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.backoffice.application.model.BackofficeOrder;
import com.wally.customersupport.cart.application.port.out.CartCheckoutCreator;
import com.wally.customersupport.order.application.service.OrderApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCartCheckoutCreator implements CartCheckoutCreator {

    private final OrderApplicationService orderService;

    @Override
    public Optional<CartCheckout> create(CreateCartCheckoutRequest request) {
        try {
            BackofficeOrder order = orderService.create(new OrderApplicationService.CreateOrderCommand(
                    request.customerReference(),
                    request.items().stream()
                            .map(item -> new OrderApplicationService.RequestedItem(item.sku(), item.quantity()))
                            .toList(),
                    "cart-" + request.cartId() + "-v" + request.cartVersion(),
                    request.cartId(),
                    request.cartVersion()));
            if (order.paymentUrl() == null || order.paymentUrl().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new CartCheckout(
                    order.id(), order.total(), order.currency(), order.paymentProvider(), order.paymentUrl()));
        } catch (RuntimeException exception) {
            log.warn("CONVERSATIONAL_CART_CHECKOUT_FAILED errorType={} cartId={}",
                    exception.getClass().getSimpleName(), request.cartId());
            return Optional.empty();
        }
    }

    @Override
    public void cancelActive(UUID cartId) {
        orderService.cancelPendingForCart(cartId);
    }
}
