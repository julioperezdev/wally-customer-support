package com.wally.customersupport.poc.whatsapp;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.wally.customersupport.poc.config.WhatsAppPocProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MetaWhatsAppClient implements WhatsAppClient {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final WhatsAppPocProperties properties;

    @Autowired
    public MetaWhatsAppClient(RestClient.Builder restClientBuilder, WhatsAppPocProperties properties) {
        this(buildRestClient(restClientBuilder, properties), properties);
    }

    MetaWhatsAppClient(RestClient restClient, WhatsAppPocProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public void sendText(String recipientWaId, String body) {
        validateConfiguration(recipientWaId, body);

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", recipientWaId,
                "type", "text",
                "text", Map.of("body", body));

        postMessage(payload);
    }

    @Override
    public void sendTemplate(
            String recipientWaId,
            String templateName,
            String languageCode,
            List<String> bodyTextParameters) {
        validateTemplateConfiguration(recipientWaId, templateName, languageCode, bodyTextParameters);

        Map<String, Object> template = new java.util.LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", languageCode));

        List<String> parameters = bodyTextParameters == null ? List.of() : List.copyOf(bodyTextParameters);
        if (!parameters.isEmpty()) {
            List<Map<String, String>> bodyParameters = parameters.stream()
                    .map(parameter -> Map.of("type", "text", "text", parameter))
                    .toList();
            template.put("components", List.of(Map.of(
                    "type", "body",
                    "parameters", bodyParameters)));
        }

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", recipientWaId,
                "type", "template",
                "template", template);

        postMessage(payload);
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
            throw new MetaWhatsAppClientException(
                    "Meta API returned HTTP " + exception.getStatusCode().value(),
                    exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new MetaWhatsAppClientException("Meta API request failed", 0);
        }
    }

    private void validateConfiguration(String recipientWaId, String body) {
        if (isBlank(recipientWaId) || isBlank(body)) {
            throw new IllegalArgumentException("Recipient and body are required");
        }
        validateApiConfiguration();
    }

    private void validateTemplateConfiguration(
            String recipientWaId,
            String templateName,
            String languageCode,
            List<String> bodyTextParameters) {
        if (isBlank(recipientWaId) || isBlank(templateName) || isBlank(languageCode)) {
            throw new IllegalArgumentException("Recipient, template name and language code are required");
        }
        if (bodyTextParameters != null && bodyTextParameters.stream().anyMatch(MetaWhatsAppClient::isBlank)) {
            throw new IllegalArgumentException("Template body parameters cannot be blank");
        }
        validateApiConfiguration();
    }

    private void validateApiConfiguration() {
        if (isBlank(properties.graphApiVersion())
                || isBlank(properties.graphApiBaseUrl())
                || isBlank(properties.phoneNumberId())
                || isBlank(properties.accessToken())) {
            throw new IllegalStateException("WhatsApp PoC client is not configured");
        }
    }

    private static RestClient buildRestClient(RestClient.Builder builder, WhatsAppPocProperties properties) {
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
