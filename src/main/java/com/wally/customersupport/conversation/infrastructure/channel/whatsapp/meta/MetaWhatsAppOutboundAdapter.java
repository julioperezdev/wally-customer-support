package com.wally.customersupport.conversation.infrastructure.channel.whatsapp.meta;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wally.customersupport.conversation.application.port.out.OutboundMessagePort;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.OutboundMessage;
import com.wally.customersupport.shared.infrastructure.config.WhatsAppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "wcs.whatsapp.adapter", havingValue = "meta")
public class MetaWhatsAppOutboundAdapter implements OutboundMessagePort {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final WhatsAppProperties properties;

    @Autowired
    public MetaWhatsAppOutboundAdapter(RestClient.Builder restClientBuilder, WhatsAppProperties properties) {
        this(buildRestClient(restClientBuilder, properties), properties);
    }

    MetaWhatsAppOutboundAdapter(RestClient restClient, WhatsAppProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public Channel channel() {
        return Channel.WHATSAPP;
    }

    @Override
    public void send(OutboundMessage message) {
        validateConfiguration(message);
        Map<String, Object> payload = message.deliveryType() == com.wally.customersupport.conversation.domain.model.DeliveryType.TEMPLATE
                ? templatePayload(message)
                : textPayload(message);
        postMessage(payload);
    }

    private Map<String, Object> textPayload(OutboundMessage message) {
        return Map.of(
                "messaging_product", "whatsapp",
                "to", message.recipientId(),
                "type", "text",
                "text", Map.of("body", message.body()));
    }

    private Map<String, Object> templatePayload(OutboundMessage message) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", message.templateName());
        template.put("language", Map.of("code", message.templateLanguageCode()));
        List<String> parameters = message.templateBodyParameters();
        if (!parameters.isEmpty()) {
            List<Map<String, String>> bodyParameters = parameters.stream()
                    .map(parameter -> Map.of("type", "text", "text", parameter))
                    .toList();
            template.put("components", List.of(Map.of(
                    "type", "body",
                    "parameters", bodyParameters)));
        }
        return Map.of(
                "messaging_product", "whatsapp",
                "to", message.recipientId(),
                "type", "template",
                "template", template);
    }

    private void postMessage(Map<String, Object> payload) {
        try {
            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{version}/{phoneNumberId}/messages")
                            .build(properties.graphApiVersion(), properties.phoneNumberId()))
                    .headers(headers -> headers.setBearerAuth(properties.accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new MetaWhatsAppException(
                    "Meta API returned HTTP " + exception.getStatusCode().value(),
                    exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new MetaWhatsAppException("Meta API request failed", 0);
        }
    }

    private void validateConfiguration(OutboundMessage message) {
        if (message == null || message.channel() != Channel.WHATSAPP || isBlank(message.recipientId())) {
            throw new IllegalArgumentException("Recipient is required");
        }
        if (message.deliveryType() == com.wally.customersupport.conversation.domain.model.DeliveryType.TEXT
                && isBlank(message.body())) {
            throw new IllegalArgumentException("Text body is required");
        }
        if (message.deliveryType() == com.wally.customersupport.conversation.domain.model.DeliveryType.TEMPLATE
                && (isBlank(message.templateName()) || isBlank(message.templateLanguageCode()))) {
            throw new IllegalArgumentException("Template name and language code are required");
        }
        if (isBlank(properties.graphApiVersion())
                || isBlank(properties.graphApiBaseUrl())
                || isBlank(properties.phoneNumberId())
                || isBlank(properties.accessToken())) {
            throw new IllegalStateException("Meta WhatsApp adapter is not configured");
        }
    }

    private static RestClient buildRestClient(RestClient.Builder builder, WhatsAppProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(safeDuration(properties.connectTimeout(), DEFAULT_CONNECT_TIMEOUT))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(safeDuration(properties.readTimeout(), DEFAULT_READ_TIMEOUT));
        return builder
                .baseUrl(properties.graphApiBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private static Duration safeDuration(Duration configured, Duration fallback) {
        return configured == null || configured.isNegative() || configured.isZero() ? fallback : configured;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
