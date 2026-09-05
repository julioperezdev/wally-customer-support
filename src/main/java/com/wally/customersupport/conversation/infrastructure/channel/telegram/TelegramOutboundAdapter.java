package com.wally.customersupport.conversation.infrastructure.channel.telegram;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import com.wally.customersupport.conversation.application.port.out.OutboundMessagePort;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.DeliveryType;
import com.wally.customersupport.conversation.domain.model.OutboundMessage;
import com.wally.customersupport.shared.infrastructure.config.TelegramProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "wcs.telegram.enabled", havingValue = "true")
@ConditionalOnProperty(name = "wcs.telegram.adapter", havingValue = "telegram")
public class TelegramOutboundAdapter implements OutboundMessagePort {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final TelegramProperties properties;

    @Autowired
    public TelegramOutboundAdapter(RestClient.Builder restClientBuilder, TelegramProperties properties) {
        this(buildRestClient(restClientBuilder, properties), properties);
    }

    TelegramOutboundAdapter(RestClient restClient, TelegramProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public Channel channel() {
        return Channel.TELEGRAM;
    }

    @Override
    public void send(OutboundMessage message) {
        validateConfiguration(message);
        try {
            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/bot{token}/sendMessage")
                            .build(properties.botToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "chat_id", message.recipientId(),
                            "text", message.body()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new TelegramException(
                    "Telegram API returned HTTP " + exception.getStatusCode().value(),
                    exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new TelegramException("Telegram API request failed", 0);
        }
    }

    private void validateConfiguration(OutboundMessage message) {
        if (message == null || message.channel() != Channel.TELEGRAM || isBlank(message.recipientId())) {
            throw new IllegalArgumentException("Telegram recipient is required");
        }
        if (message.deliveryType() != DeliveryType.TEXT || isBlank(message.body())) {
            throw new IllegalArgumentException("Telegram supports text messages only");
        }
        if (isBlank(properties.apiBaseUrl()) || isBlank(properties.botToken())) {
            throw new IllegalStateException("Telegram adapter is not configured");
        }
    }

    private static RestClient buildRestClient(RestClient.Builder builder, TelegramProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(safeDuration(properties.connectTimeout(), DEFAULT_CONNECT_TIMEOUT))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(safeDuration(properties.readTimeout(), DEFAULT_READ_TIMEOUT));
        return builder
                .baseUrl(properties.apiBaseUrl())
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
