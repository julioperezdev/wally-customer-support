package com.wally.customersupport.order.application.service;

import java.util.List;
import java.util.Optional;

import com.wally.customersupport.backoffice.application.model.BackofficeOrder;
import com.wally.customersupport.conversation.application.port.out.PurchaseLinkCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Adapts the existing idempotent order use case to the conversational
 * checkout boundary. The payment provider remains selected by the order
 * module and is never exposed to the conversation domain.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderPurchaseLinkCreator implements PurchaseLinkCreator {

    private final OrderApplicationService orderService;

    @Override
    public Optional<PurchaseLink> create(CreatePurchaseLinkRequest request) {
        try {
            BackofficeOrder order = orderService.create(new OrderApplicationService.CreateOrderCommand(
                    request.customerReference(),
                    List.of(new OrderApplicationService.RequestedItem(request.sku(), request.quantity())),
                    request.idempotencyKey()));
            if (order.paymentUrl() == null || order.paymentUrl().isBlank() || order.items().size() != 1) {
                return Optional.empty();
            }
            BackofficeOrder.Item item = order.items().getFirst();
            return Optional.of(new PurchaseLink(
                    order.id(),
                    item.productName(),
                    item.sku(),
                    item.quantity(),
                    order.total(),
                    order.currency(),
                    order.paymentProvider(),
                    order.paymentUrl()));
        } catch (RuntimeException exception) {
            log.warn("CONVERSATIONAL_PURCHASE_LINK_FAILED errorType={} conversationId={}",
                    exception.getClass().getSimpleName(), request.conversationId());
            return Optional.empty();
        }
    }
}
