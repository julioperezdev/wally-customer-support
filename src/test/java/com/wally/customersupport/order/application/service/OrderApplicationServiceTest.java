package com.wally.customersupport.order.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.wally.customersupport.catalog.infrastructure.repository.postgres.CatalogVariantJpaEntity;
import com.wally.customersupport.catalog.infrastructure.repository.postgres.SpringDataCatalogVariantRepository;
import com.wally.customersupport.order.application.port.out.OrderRepository;
import com.wally.customersupport.order.application.port.out.PaymentGateway;
import com.wally.customersupport.order.infrastructure.config.PaymentProperties;
import com.wally.customersupport.order.infrastructure.repository.postgres.OrderJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SpringDataCatalogVariantRepository variantRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private CatalogVariantJpaEntity variant;

    private OrderApplicationService service;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(
                "mock", "ARS", "", Duration.ofSeconds(5),
                new PaymentProperties.MercadoPago(null, null),
                new PaymentProperties.Webhook(false, null));
        service = new OrderApplicationService(
                orderRepository,
                variantRepository,
                paymentGateway,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void pricesFromTheLockedCatalogVariantAndReplaysAnIdempotentRequest() {
        when(variantRepository.findBySku("RP-REM-NP-NEG-M")).thenReturn(Optional.of(variant));
        when(variant.isActive()).thenReturn(true);
        when(variant.getStock()).thenReturn(12);
        when(variant.getCurrency()).thenReturn("ARS");
        when(variant.getPrice()).thenReturn(new BigDecimal("18900.00"));
        when(variant.getProductName()).thenReturn("Remera NullPointer");
        OrderJpaEntity[] saved = new OrderJpaEntity[1];
        when(orderRepository.findByIdempotencyKey("request-1"))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation -> Optional.of(saved[0]));
        when(orderRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            return saved[0];
        });
        when(paymentGateway.createPreference(any())).thenReturn(
                new PaymentGateway.PaymentPreference("mock", "preference-1", "https://pay.invalid/1"));

        var first = service.create(new OrderApplicationService.CreateOrderCommand(
                "telegram:42", List.of(new OrderApplicationService.RequestedItem("rp-rem-np-neg-m", 2)), "request-1"));
        var second = service.create(new OrderApplicationService.CreateOrderCommand(
                "telegram:42", List.of(new OrderApplicationService.RequestedItem("RP-REM-NP-NEG-M", 2)), "request-1"));

        assertEquals(first.id(), second.id());
        assertEquals(new BigDecimal("37800.00"), first.total());
        assertEquals("https://pay.invalid/1", first.paymentUrl());
        verify(paymentGateway, times(1)).createPreference(any());
    }

    @Test
    void rejectsInsufficientStockBeforeCreatingAPaymentLink() {
        when(variantRepository.findBySku("SKU-1")).thenReturn(Optional.of(variant));
        when(variant.isActive()).thenReturn(true);
        when(variant.getStock()).thenReturn(1);

        var exception = assertThrows(OrderApplicationService.InvalidOrderException.class,
                () -> service.create(new OrderApplicationService.CreateOrderCommand(
                        "telegram:42", List.of(new OrderApplicationService.RequestedItem("SKU-1", 2)), "request-2")));

        assertEquals("INSUFFICIENT_STOCK", exception.getMessage());
        verify(orderRepository, never()).saveAndFlush(any());
        verify(paymentGateway, never()).createPreference(any());
    }

}
