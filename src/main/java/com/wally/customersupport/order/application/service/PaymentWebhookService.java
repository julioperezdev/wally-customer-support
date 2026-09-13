package com.wally.customersupport.order.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.order.application.port.out.OrderRepository;
import com.wally.customersupport.order.application.port.out.PaymentGateway;
import com.wally.customersupport.order.domain.model.OrderStatus;
import com.wally.customersupport.order.infrastructure.config.PaymentProperties;
import com.wally.customersupport.order.infrastructure.repository.postgres.PaymentEventJpaEntity;
import com.wally.customersupport.order.infrastructure.repository.postgres.SpringDataPaymentEventRepository;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookService {

    private final OrderRepository orderRepository;
    private final SpringDataPaymentEventRepository paymentEventRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentProperties paymentProperties;
    private final Clock clock;

    @Transactional
    public Result process(WebhookCommand command) {
        String provider = paymentProperties.effectiveProvider();
        if (paymentEventRepository.existsByProviderAndProviderEventId(provider, command.eventId())) {
            StructuredEventLog.info(log, "PAYMENT_WEBHOOK_DEDUPLICATED", java.util.Map.of(
                    "provider", provider, "eventId", command.eventId(), "result", "DUPLICATE"));
            return new Result("DUPLICATE", null, null);
        }

        PaymentGateway.PaymentNotification payment = paymentGateway.getPayment(command.paymentId());
        UUID orderId = parseUuid(payment.externalReference());
        var order = orderId == null ? java.util.Optional.<com.wally.customersupport.order.infrastructure.repository.postgres.OrderJpaEntity>empty()
                : orderRepository.findByIdWithItems(orderId);
        OrderStatus nextStatus = mapStatus(payment.status());
        if (order.isEmpty()) {
            paymentEventRepository.save(new PaymentEventJpaEntity(
                    null,
                    provider,
                    command.eventId(),
                    command.eventType(),
                    payment.providerPaymentId(),
                    nextStatus.name(),
                    command.payloadHash(),
                    payment.occurredAt(),
                    Instant.now(clock)));
            StructuredEventLog.info(log, "PAYMENT_WEBHOOK_IGNORED", java.util.Map.of(
                    "provider", provider, "eventId", command.eventId(), "paymentId", payment.providerPaymentId(),
                    "paymentStatus", nextStatus.name(), "result", "IGNORED"));
            return new Result("IGNORED", null, nextStatus.name());
        }

        var current = order.get();
        current.applyPayment(nextStatus, payment.providerPaymentId(), Instant.now(clock));
        orderRepository.saveAndFlush(current);
        paymentEventRepository.save(new PaymentEventJpaEntity(
                current.getId(),
                provider,
                command.eventId(),
                command.eventType(),
                payment.providerPaymentId(),
                nextStatus.name(),
                command.payloadHash(),
                payment.occurredAt(),
                Instant.now(clock)));
        StructuredEventLog.info(log, "PAYMENT_WEBHOOK_APPLIED", java.util.Map.of(
                "orderId", current.getId(), "provider", provider, "eventId", command.eventId(),
                "paymentId", payment.providerPaymentId(), "paymentStatus", nextStatus.name(), "result", "APPLIED"));
        return new Result("APPLIED", current.getId(), nextStatus.name());
    }

    private static OrderStatus mapStatus(String status) {
        if (status == null) return OrderStatus.PENDING_PAYMENT;
        return switch (status.toLowerCase(java.util.Locale.ROOT)) {
            case "approved" -> OrderStatus.PAID;
            case "rejected" -> OrderStatus.REJECTED;
            case "cancelled", "canceled" -> OrderStatus.CANCELLED;
            case "expired" -> OrderStatus.EXPIRED;
            default -> OrderStatus.PENDING_PAYMENT;
        };
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public record WebhookCommand(
            String eventId,
            String eventType,
            String paymentId,
            String payloadHash) {

        public WebhookCommand {
            if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId is required");
            if (paymentId == null || paymentId.isBlank()) throw new IllegalArgumentException("paymentId is required");
            eventType = eventType == null || eventType.isBlank() ? "payment" : eventType;
            payloadHash = payloadHash == null || payloadHash.isBlank() ? "unknown" : payloadHash;
        }
    }

    public record Result(String status, UUID orderId, String paymentStatus) {
    }

    public static String sha256(String payload) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
