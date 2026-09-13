package com.wally.customersupport.order.application.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.wally.customersupport.backoffice.application.model.BackofficeOrder;
import com.wally.customersupport.backoffice.application.model.BackofficeOrderPage;
import com.wally.customersupport.catalog.infrastructure.repository.postgres.CatalogVariantJpaEntity;
import com.wally.customersupport.catalog.infrastructure.repository.postgres.SpringDataCatalogVariantRepository;
import com.wally.customersupport.order.application.port.out.OrderRepository;
import com.wally.customersupport.order.application.port.out.PaymentGateway;
import com.wally.customersupport.order.infrastructure.config.PaymentProperties;
import com.wally.customersupport.order.infrastructure.repository.postgres.OrderItemJpaEntity;
import com.wally.customersupport.order.infrastructure.repository.postgres.OrderJpaEntity;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderApplicationService {

    private static final int MAX_ITEMS = 20;
    private static final int MAX_QUANTITY_PER_ITEM = 100;

    private final OrderRepository orderRepository;
    private final SpringDataCatalogVariantRepository variantRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentProperties paymentProperties;
    private final Clock clock;

    @Transactional
    public BackofficeOrder create(CreateOrderCommand command) {
        String idempotencyKey = required(command.idempotencyKey(), "idempotencyKey", 128);
        List<RequestedItem> requestedItems = normalizeItems(command.items());
        String requestHash = requestHash(command.customerReference(), requestedItems);

        var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            OrderJpaEntity order = existing.get();
            if (!requestHash.equals(order.getRequestHash())) {
                throw new IdempotencyConflictException();
            }
            if (order.getPaymentUrl() == null || order.getPaymentUrl().isBlank()) {
                createPaymentPreference(order);
            }
            return toView(order);
        }

        List<PricedItem> pricedItems = requestedItems.stream()
                .map(this::priceAndValidateStock)
                .toList();
        BigDecimal total = pricedItems.stream()
                .map(PricedItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant now = Instant.now(clock);
        OrderJpaEntity order = new OrderJpaEntity(
                UUID.randomUUID(),
                normalizeOptional(command.customerReference(), 128),
                paymentProperties.effectiveCurrency(),
                total,
                paymentProperties.effectiveProvider(),
                idempotencyKey,
                requestHash,
                now);
        pricedItems.forEach(item -> order.addItem(
                item.sku(), item.productName(), item.quantity(), item.unitPrice(), item.currency(), item.lineTotal()));
        try {
            orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException exception) {
            OrderJpaEntity concurrent = orderRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> exception);
            if (!requestHash.equals(concurrent.getRequestHash())) {
                throw new IdempotencyConflictException();
            }
            return toView(concurrent);
        }

        createPaymentPreference(order);
        return toView(order);
    }

    @Transactional(readOnly = true)
    public BackofficeOrder find(UUID id) {
        return orderRepository.findByIdWithItems(id)
                .map(OrderApplicationService::toView)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public BackofficeOrderPage findPage(String status, int page, int size) {
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.min(100, Math.max(1, size));
        List<OrderJpaEntity> orders = orderRepository.findByStatus(status, (normalizedPage + 1) * normalizedSize + 1);
        int from = Math.min(normalizedPage * normalizedSize, orders.size());
        int to = Math.min(from + normalizedSize, orders.size());
        return new BackofficeOrderPage(
                orders.subList(from, to).stream().map(OrderApplicationService::toView).toList(),
                normalizedPage,
                normalizedSize,
                orders.size() > to);
    }

    private void createPaymentPreference(OrderJpaEntity order) {
        try {
            PaymentGateway.PaymentPreference preference = paymentGateway.createPreference(
                    new PaymentGateway.CreatePreferenceRequest(
                            order.getId(),
                            order.getCurrency(),
                            paymentProperties.effectiveNotificationUrl(),
                            order.getItems().stream()
                                    .map(item -> new PaymentGateway.Item(
                                            item.getSku(),
                                            item.getProductName(),
                                            item.getQuantity(),
                                            item.getUnitPrice(),
                                            item.getCurrency()))
                                    .toList()));
            order.attachPaymentPreference(
                    required(preference.preferenceId(), "preferenceId", 128),
                    required(preference.checkoutUrl(), "checkoutUrl", 2048),
                    Instant.now(clock));
            orderRepository.saveAndFlush(order);
        } catch (RuntimeException exception) {
            StructuredEventLog.warn(log, "PAYMENT_PREFERENCE_CREATION_FAILED", java.util.Map.of(
                    "operation", "orders.create.payment-preference",
                    "orderId", order.getId(),
                    "provider", paymentProperties.effectiveProvider(),
                    "errorType", exception.getClass().getSimpleName()));
        }
    }

    private PricedItem priceAndValidateStock(RequestedItem requestedItem) {
        CatalogVariantJpaEntity variant = variantRepository.findBySku(requestedItem.sku())
                .orElseThrow(() -> new InvalidOrderException("SKU_NOT_FOUND"));
        if (!variant.isActive()) {
            throw new InvalidOrderException("SKU_INACTIVE");
        }
        if (variant.getStock() < requestedItem.quantity()) {
            throw new InvalidOrderException("INSUFFICIENT_STOCK");
        }
        String currency = variant.getCurrency();
        if (!paymentProperties.effectiveCurrency().equalsIgnoreCase(currency)) {
            throw new InvalidOrderException("CURRENCY_NOT_SUPPORTED");
        }
        BigDecimal lineTotal = variant.getPrice().multiply(BigDecimal.valueOf(requestedItem.quantity()));
        return new PricedItem(
                requestedItem.sku(),
                variant.getProductName(),
                requestedItem.quantity(),
                variant.getPrice(),
                currency,
                lineTotal);
    }

    private static List<RequestedItem> normalizeItems(List<RequestedItem> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_ITEMS) {
            throw new InvalidOrderException("INVALID_ITEMS");
        }
        List<RequestedItem> normalized = items.stream()
                .map(item -> new RequestedItem(
                        required(item == null ? null : item.sku(), "sku", 80).toUpperCase(Locale.ROOT),
                        item == null ? 0 : item.quantity()))
                .sorted(java.util.Comparator.comparing(RequestedItem::sku))
                .toList();
        if (normalized.stream().anyMatch(item -> item.quantity() < 1 || item.quantity() > MAX_QUANTITY_PER_ITEM)
                || new HashSet<>(normalized.stream().map(RequestedItem::sku).toList()).size() != normalized.size()) {
            throw new InvalidOrderException("INVALID_ITEMS");
        }
        return normalized;
    }

    private static String requestHash(String customerReference, List<RequestedItem> items) {
        String canonical = normalizeOptional(customerReference, 128) + "|"
                + items.stream().map(item -> item.sku() + ":" + item.quantity()).reduce((a, b) -> a + "," + b).orElse("");
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static BackofficeOrder toView(OrderJpaEntity order) {
        return new BackofficeOrder(
                order.getId(),
                order.getCustomerReference(),
                order.getStatus().name(),
                order.getCurrency(),
                order.getTotal(),
                order.getPaymentProvider(),
                order.getPaymentPreferenceId(),
                order.getPaymentUrl(),
                order.getExternalPaymentId(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getItems().stream().map(OrderApplicationService::toItem).toList());
    }

    private static BackofficeOrder.Item toItem(OrderItemJpaEntity item) {
        return new BackofficeOrder.Item(
                item.getSku(), item.getProductName(), item.getQuantity(), item.getUnitPrice(), item.getCurrency(), item.getLineTotal());
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new InvalidOrderException("INVALID_" + field.toUpperCase());
        }
        return value.trim();
    }

    private static String normalizeOptional(String value, int max) {
        if (value == null || value.isBlank()) return "";
        if (value.strip().length() > max) throw new InvalidOrderException("INVALID_CUSTOMER_REFERENCE");
        return value.strip();
    }

    public record CreateOrderCommand(
            String customerReference,
            List<RequestedItem> items,
            String idempotencyKey) {
    }

    public record RequestedItem(String sku, int quantity) {
    }

    private record PricedItem(
            String sku,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            String currency,
            BigDecimal lineTotal) {
    }

    public static class InvalidOrderException extends RuntimeException {
        public InvalidOrderException(String code) {
            super(code);
        }
    }

    public static class IdempotencyConflictException extends RuntimeException {
    }

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(UUID id) {
            super("Order not found: " + id);
        }
    }
}
