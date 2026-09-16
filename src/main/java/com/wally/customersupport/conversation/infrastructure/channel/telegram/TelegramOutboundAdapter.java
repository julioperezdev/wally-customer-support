package com.wally.customersupport.conversation.infrastructure.channel.telegram;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import com.wally.customersupport.conversation.application.port.out.OutboundMessagePort;
import com.wally.customersupport.conversation.application.port.out.OutboundMediaUrlResolver;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.DeliveryType;
import com.wally.customersupport.conversation.domain.model.OutboundMessage;
import com.wally.customersupport.conversation.infrastructure.media.NoOpOutboundMediaUrlResolver;
import com.wally.customersupport.shared.infrastructure.config.TelegramProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(name = "wcs.telegram.enabled", havingValue = "true")
@ConditionalOnProperty(name = "wcs.telegram.adapter", havingValue = "telegram")
@Slf4j
public class TelegramOutboundAdapter implements OutboundMessagePort {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_CAPTION_CHARACTERS = 1024;

    private final RestClient restClient;
    private final TelegramProperties properties;
    private final OutboundMediaUrlResolver mediaUrlResolver;

    @Autowired
    public TelegramOutboundAdapter(
            RestClient.Builder restClientBuilder,
            TelegramProperties properties,
            OutboundMediaUrlResolver mediaUrlResolver) {
        this(buildRestClient(restClientBuilder, properties), properties, mediaUrlResolver);
    }

    TelegramOutboundAdapter(RestClient restClient, TelegramProperties properties) {
        this(restClient, properties, new NoOpOutboundMediaUrlResolver());
    }

    TelegramOutboundAdapter(
            RestClient restClient,
            TelegramProperties properties,
            OutboundMediaUrlResolver mediaUrlResolver) {
        this.restClient = restClient;
        this.properties = properties;
        this.mediaUrlResolver = mediaUrlResolver;
    }

    @Override
    public Channel channel() {
        return Channel.TELEGRAM;
    }

    @Override
    public void send(OutboundMessage message) {
        validateConfiguration(message);
        try {
            if (message.deliveryType() == DeliveryType.IMAGE) {
                sendImageOrText(message);
            } else {
                sendText(message);
            }
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
        if (message.deliveryType() == DeliveryType.TEMPLATE) {
            throw new IllegalArgumentException("Telegram supports text messages only");
        }
        if (isBlank(message.body())) {
            throw new IllegalArgumentException("Telegram message body is required");
        }
        if (message.deliveryType() == DeliveryType.IMAGE && isBlank(message.mediaReference())) {
            throw new IllegalArgumentException("Telegram image reference is required");
        }
        if (isBlank(properties.apiBaseUrl()) || isBlank(properties.botToken())) {
            throw new IllegalStateException("Telegram adapter is not configured");
        }
    }

    private void sendText(OutboundMessage message) {
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
    }

    private void sendImageOrText(OutboundMessage message) {
        if (message.body().length() > MAX_CAPTION_CHARACTERS) {
            log.info("OUTBOUND_MEDIA_FALLBACK channel=TELEGRAM deliveryType=IMAGE reason=CAPTION_TOO_LONG");
            sendText(message);
            return;
        }
        Optional<String> mediaUrl = resolveMediaUrl(message.mediaReference());
        if (mediaUrl.isEmpty()) {
            log.info("OUTBOUND_MEDIA_FALLBACK channel=TELEGRAM deliveryType=IMAGE reason=MEDIA_URL_UNAVAILABLE");
            sendText(message);
            return;
        }
        restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/bot{token}/sendPhoto")
                        .build(properties.botToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "chat_id", message.recipientId(),
                        "photo", mediaUrl.get(),
                        "caption", message.body()))
                .retrieve()
                .toBodilessEntity();
    }

    private Optional<String> resolveMediaUrl(String mediaReference) {
        try {
            return mediaUrlResolver.resolve(mediaReference).filter(url -> !isBlank(url));
        } catch (RuntimeException exception) {
            log.warn("OUTBOUND_MEDIA_FALLBACK channel=TELEGRAM deliveryType=IMAGE errorType={}",
                    exception.getClass().getSimpleName());
            return Optional.empty();
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
