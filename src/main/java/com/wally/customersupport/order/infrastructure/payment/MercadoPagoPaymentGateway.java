package com.wally.customersupport.order.infrastructure.payment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import com.wally.customersupport.order.application.port.out.PaymentGateway;
import com.wally.customersupport.order.infrastructure.config.PaymentProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Mercado Pago Checkout Pro adapter. The rest of WCS only sees PaymentGateway. */
public class MercadoPagoPaymentGateway implements PaymentGateway {

    private final RestClient restClient;
    private final PaymentProperties properties;

    public MercadoPagoPaymentGateway(RestClient.Builder builder, PaymentProperties properties) {
        this.properties = properties;
        String accessToken = properties.effectiveMercadoPago().accessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Mercado Pago access token must be configured");
        }
        this.restClient = builder
                .baseUrl(properties.effectiveMercadoPago().effectiveBaseUrl())
                .requestFactory(requestFactory(properties))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.trim())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public PaymentPreference createPreference(CreatePreferenceRequest request) {
        List<Map<String, Object>> items = request.items().stream()
                .map(item -> {
                    Map<String, Object> value = new HashMap<>();
                    value.put("id", item.sku());
                    value.put("title", item.title());
                    value.put("quantity", item.quantity());
                    value.put("currency_id", item.currency());
                    value.put("unit_price", item.unitPrice());
                    return value;
                })
                .toList();
        Map<String, Object> body = new HashMap<>();
        body.put("items", items);
        body.put("external_reference", request.orderId().toString());
        if (!request.notificationUrl().isBlank()) {
            body.put("notification_url", request.notificationUrl());
        }

        JsonNode response = restClient.post()
                .uri("/checkout/preferences")
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        String preferenceId = text(response, "id");
        if (preferenceId == null || preferenceId.isBlank()) {
            throw new IllegalStateException("Mercado Pago returned an incomplete preference");
        }
        String sandboxUrl = text(response, "sandbox_init_point", "sandboxInitPoint");
        String checkoutUrl = sandboxUrl == null || sandboxUrl.isBlank()
                ? text(response, "init_point", "initPoint")
                : sandboxUrl;
        if (checkoutUrl == null || checkoutUrl.isBlank()) {
            throw new IllegalStateException("Mercado Pago returned no checkout URL");
        }
        return new PaymentPreference("mercadopago", preferenceId, checkoutUrl);
    }

    @Override
    public PaymentNotification getPayment(String providerPaymentId) {
        JsonNode response = restClient.get()
                .uri("/v1/payments/{id}", providerPaymentId)
                .retrieve()
                .body(JsonNode.class);
        String paymentId = text(response, "id");
        String status = text(response, "status");
        if (paymentId == null || status == null) {
            throw new IllegalStateException("Mercado Pago returned an incomplete payment");
        }
        return new PaymentNotification(
                paymentId,
                text(response, "external_reference", "externalReference"),
                status,
                text(response, "status_detail", "statusDetail"),
                firstInstant(response, "date_approved", "dateApproved", "date_created", "dateCreated"));
    }

    private static JdkClientHttpRequestFactory requestFactory(PaymentProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.effectiveRequestTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.effectiveRequestTimeout());
        return requestFactory;
    }

    private static String text(JsonNode node, String... fields) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) return value.asText();
        }
        return null;
    }

    private static Instant firstInstant(JsonNode node, String... fields) {
        String value = text(node, fields);
        if (value == null) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }
}
