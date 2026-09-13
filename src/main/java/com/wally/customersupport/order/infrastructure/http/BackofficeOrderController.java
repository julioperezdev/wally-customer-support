package com.wally.customersupport.order.infrastructure.http;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wally.customersupport.backoffice.application.model.BackofficeOrder;
import com.wally.customersupport.backoffice.application.model.BackofficeOrderPage;
import com.wally.customersupport.backoffice.application.service.BackofficeAccessService;
import com.wally.customersupport.order.application.service.OrderApplicationService;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/backoffice/orders")
@RequiredArgsConstructor
@Slf4j
public class BackofficeOrderController {

    private final BackofficeAccessService accessService;
    private final OrderApplicationService orderService;

    @GetMapping
    public ResponseEntity<?> list(
            Principal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        BackofficeAccessService.Decision decision = accessService.authorize(
                "backoffice.orders.read", actorId(principal));
        if (!decision.authorized()) return denied(decision, "orders.list");
        try {
            BackofficeOrderPage result = orderService.findPage(status, page, limit);
            StructuredEventLog.info(log, "BACKOFFICE_ORDERS_VIEWED", Map.of(
                    "operation", "orders.list", "resultCount", result.items().size()));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_ORDER_STATUS"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> find(Principal principal, @PathVariable UUID id) {
        BackofficeAccessService.Decision decision = accessService.authorize(
                "backoffice.orders.read", actorId(principal));
        if (!decision.authorized()) return denied(decision, "orders.find");
        try {
            return ResponseEntity.ok(orderService.find(id));
        } catch (OrderApplicationService.OrderNotFoundException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<?> create(
            Principal principal,
            @RequestBody CreateOrderRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        BackofficeAccessService.Decision decision = accessService.authorize(
                "backoffice.orders.write", actorId(principal));
        if (!decision.authorized()) return denied(decision, "orders.create");
        try {
            BackofficeOrder result = orderService.create(new OrderApplicationService.CreateOrderCommand(
                    request.customerReference(),
                    request.items() == null ? List.of() : request.items().stream()
                            .map(item -> new OrderApplicationService.RequestedItem(item.sku(), item.quantity()))
                            .toList(),
                    idempotencyKey));
            StructuredEventLog.info(log, "BACKOFFICE_ORDER_CREATED", Map.of(
                    "operation", "orders.create",
                    "orderId", result.id(),
                    "status", result.status(),
                    "paymentLinkCreated", result.paymentUrl() != null));
            if (result.paymentUrl() == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "code", "PAYMENT_LINK_UNAVAILABLE", "order", result));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (OrderApplicationService.IdempotencyConflictException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "IDEMPOTENCY_KEY_REUSED"));
        } catch (OrderApplicationService.InvalidOrderException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", exception.getMessage()));
        }
    }

    private ResponseEntity<Map<String, String>> denied(
            BackofficeAccessService.Decision decision,
            String operation) {
        StructuredEventLog.warn(log, "BACKOFFICE_ACCESS_DENIED", Map.of(
                "operation", operation, "reason", decision.reason()));
        return ResponseEntity.status(decision.status()).body(Map.of("code", decision.reason()));
    }

    private static String actorId(Principal principal) {
        return principal == null ? null : principal.getName();
    }

    public record CreateOrderRequest(String customerReference, List<ItemRequest> items) {
    }

    public record ItemRequest(String sku, int quantity) {
    }
}
