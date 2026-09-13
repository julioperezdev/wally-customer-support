package com.wally.customersupport.order.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.order.application.port.out.OrderRepository;
import com.wally.customersupport.order.application.port.out.PaymentGateway;
import com.wally.customersupport.order.infrastructure.config.PaymentProperties;
import com.wally.customersupport.order.infrastructure.repository.postgres.OrderJpaEntity;
import com.wally.customersupport.order.infrastructure.repository.postgres.PaymentEventJpaEntity;
import com.wally.customersupport.order.infrastructure.repository.postgres.SpringDataPaymentEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SpringDataPaymentEventRepository paymentEventRepository;

    @Mock
    private PaymentGateway paymentGateway;

    private PaymentWebhookService service;

    @BeforeEach
    void setUp() {
        service = new PaymentWebhookService(
                orderRepository,
                paymentEventRepository,
                paymentGateway,
                new PaymentProperties(
                        "mock", "ARS", "", Duration.ofSeconds(5),
                        new PaymentProperties.MercadoPago(null, null),
                        new PaymentProperties.Webhook(false, null)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void appliesApprovedPaymentAndDeduplicatesTheSameEvent() {
        UUID orderId = UUID.randomUUID();
        OrderJpaEntity order = new OrderJpaEntity(
                orderId, "telegram:42", "ARS", new BigDecimal("18900.00"), "mock",
                "request-1", "hash", NOW);
        when(paymentEventRepository.existsByProviderAndProviderEventId("mock", "event-1"))
                .thenReturn(false)
                .thenReturn(true);
        when(paymentGateway.getPayment("payment-1")).thenReturn(new PaymentGateway.PaymentNotification(
                "payment-1", orderId.toString(), "approved", "accredited", NOW));
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));

        var command = new PaymentWebhookService.WebhookCommand("event-1", "payment.updated", "payment-1", "payload-hash");
        var applied = service.process(command);
        var duplicate = service.process(command);

        assertEquals("APPLIED", applied.status());
        assertEquals("PAID", applied.paymentStatus());
        assertEquals("PAID", order.getStatus().name());
        assertEquals("DUPLICATE", duplicate.status());
        verify(orderRepository).saveAndFlush(order);
        verify(paymentEventRepository).save(any(PaymentEventJpaEntity.class));
        verify(paymentGateway).getPayment("payment-1");
    }

    @Test
    void recordsAValidEventWhenTheReferencedOrderIsUnknown() {
        when(paymentEventRepository.existsByProviderAndProviderEventId("mock", "event-2")).thenReturn(false);
        when(paymentGateway.getPayment("payment-2")).thenReturn(new PaymentGateway.PaymentNotification(
                "payment-2", "not-an-order", "rejected", "cc_rejected", NOW));

        var result = service.process(new PaymentWebhookService.WebhookCommand(
                "event-2", "payment.updated", "payment-2", "payload-hash"));

        assertEquals("IGNORED", result.status());
        assertEquals("REJECTED", result.paymentStatus());
        verify(paymentEventRepository).save(any(PaymentEventJpaEntity.class));
        verify(orderRepository, never()).saveAndFlush(any());
    }
}
