package com.wally.customersupport.conversation.infrastructure.media.s3;

import java.time.Duration;
import java.util.Optional;

import com.wally.customersupport.conversation.application.port.out.OutboundMediaUrlResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Creates short-lived S3 GET URLs only when an outbound adapter is about to
 * deliver an image. S3 keys are never exposed in the outbound payload or logs.
 */
@Component
@ConditionalOnProperty(name = "wcs.backoffice.media.enabled", havingValue = "true")
@Slf4j
public class S3OutboundMediaUrlResolver implements OutboundMediaUrlResolver {

    private static final Duration URL_DURATION = Duration.ofMinutes(5);

    private final String bucket;
    private final String prefix;
    private final String region;

    public S3OutboundMediaUrlResolver(
            @Value("${wcs.backoffice.media.bucket:}") String bucket,
            @Value("${wcs.backoffice.media.prefix:wcs/catalog}") String prefix,
            @Value("${wcs.backoffice.media.region:us-east-1}") String region) {
        this.bucket = bucket;
        this.prefix = prefix;
        this.region = region;
    }

    @Override
    public Optional<String> resolve(String mediaReference) {
        if (isBlank(bucket) || isBlank(mediaReference) || !isAllowedReference(mediaReference)) {
            return Optional.empty();
        }
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(region))
                .build()) {
            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(mediaReference)
                    .build();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(URL_DURATION)
                    .getObjectRequest(objectRequest)
                    .build();
            return Optional.of(presigner.presignGetObject(presignRequest).url().toString());
        } catch (RuntimeException exception) {
            log.warn("OUTBOUND_MEDIA_URL_UNAVAILABLE outcome=FALLBACK errorType={}",
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private boolean isAllowedReference(String mediaReference) {
        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        return mediaReference.startsWith(normalizedPrefix)
                && !mediaReference.contains("..")
                && !mediaReference.contains("\\");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
