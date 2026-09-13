package com.wally.customersupport.order.infrastructure.http;

import java.util.Map;

import com.wally.customersupport.order.application.service.OrderApplicationService;
import com.wally.customersupport.order.application.service.PaymentWebhookService;
import com.wally.customersupport.order.infrastructure.payment.PaymentWebhookSignatureVerifier;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/webhook/mercadopago")
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoWebhookController {

    private final ObjectMapper objectMapper;
    private final PaymentWebhookSignatureVerifier signatureVerifier;
    private final PaymentWebhookService webhookService;

    @PostMapping
    public ResponseEntity<?> receive(
            @RequestBody String payload,
            @RequestHeader(name = "x-signature", required = false) String signature,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestParam(name = "data.id", required = false) String queryDataId) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String notificationId = text(root, "id");
            String type = text(root, "type");
            String eventType = text(root, "action");
            String dataId = text(root.path("data"), "id");
            if (dataId == null || dataId.isBlank()) dataId = queryDataId;
            if (!"payment".equalsIgnoreCase(type) || dataId == null || dataId.isBlank()) {
                return ResponseEntity.accepted().body(Map.of("status", "IGNORED"));
            }
            if (!signatureVerifier.verify(signature, requestId, dataId)) {
                StructuredEventLog.warn(log, "PAYMENT_WEBHOOK_REJECTED", Map.of(
                        "provider", "mercadopago", "result", "INVALID_SIGNATURE"));
                return ResponseEntity.status(401).body(Map.of("code", "INVALID_WEBHOOK_SIGNATURE"));
            }
            String eventId = notificationId == null || notificationId.isBlank()
                    ? (requestId == null ? dataId : requestId + ":" + dataId)
                    : notificationId;
            var result = webhookService.process(new PaymentWebhookService.WebhookCommand(
                    eventId,
                    eventType == null ? type : eventType,
                    dataId,
                    PaymentWebhookService.sha256(payload)));
            return ResponseEntity.ok(result);
        } catch (OrderApplicationService.InvalidOrderException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", exception.getMessage()));
        } catch (JacksonException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_WEBHOOK_PAYLOAD"));
        } catch (Exception exception) {
            StructuredEventLog.warn(log, "PAYMENT_WEBHOOK_PROCESSING_FAILED", Map.of(
                    "provider", "mercadopago", "errorType", exception.getClass().getSimpleName()));
            return ResponseEntity.status(503).body(Map.of("code", "WEBHOOK_PROCESSING_UNAVAILABLE"));
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
