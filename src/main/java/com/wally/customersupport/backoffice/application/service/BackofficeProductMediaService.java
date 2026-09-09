package com.wally.customersupport.backoffice.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.wally.customersupport.backoffice.application.model.BackofficeImageUpload;
import com.wally.customersupport.catalog.infrastructure.repository.postgres.SpringDataCatalogProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class BackofficeProductMediaService {

    private static final long MAX_IMAGE_BYTES = 5_000_000;
    private static final Duration URL_DURATION = Duration.ofMinutes(10);

    private final SpringDataCatalogProductRepository productRepository;
    private final Clock clock;

    @Value("${wcs.backoffice.media.enabled:false}")
    private boolean enabled;

    @Value("${wcs.backoffice.media.bucket:}")
    private String bucket;

    @Value("${wcs.backoffice.media.prefix:wcs/catalog}")
    private String prefix;

    @Value("${wcs.backoffice.media.region:us-east-1}")
    private String region;

    public BackofficeImageUpload requestUpload(
            UUID productId,
            String fileName,
            String contentType,
            long contentLength) {
        ensureEnabled();
        if (!productRepository.existsById(productId)) {
            throw new IllegalArgumentException("product not found");
        }
        String normalizedContentType = normalizeContentType(contentType);
        if (contentLength <= 0 || contentLength > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("image size is invalid");
        }
        String objectKey = objectKey(productId, fileName, normalizedContentType);
        Instant expiresAt = Instant.now(clock).plus(URL_DURATION);
        try (S3Presigner presigner = S3Presigner.builder().region(Region.of(region)).build()) {
            PutObjectRequest putObject = PutObjectRequest.builder()
                    .bucket(requiredBucket())
                    .key(objectKey)
                    .contentType(normalizedContentType)
                    .contentLength(contentLength)
                    .build();
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(URL_DURATION)
                    .putObjectRequest(putObject)
                    .build();
            String uploadUrl = presigner.presignPutObject(presignRequest).url().toExternalForm();
            return new BackofficeImageUpload(productId, objectKey, uploadUrl, expiresAt);
        }
    }

    @Transactional
    public void confirmUpload(UUID productId, String objectKey) {
        ensureEnabled();
        if (objectKey == null || !objectKey.startsWith(prefix + "/" + productId + "/")) {
            throw new IllegalArgumentException("object key is outside the product prefix");
        }
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("product not found"));
        product.setImageObjectKey(objectKey, Instant.now(clock));
        productRepository.save(product);
    }

    private void ensureEnabled() {
        if (!enabled) {
            throw new IllegalStateException("backoffice media is disabled");
        }
    }

    private String requiredBucket() {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("backoffice media bucket is not configured");
        }
        return bucket;
    }

    private String objectKey(UUID productId, String fileName, String contentType) {
        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".webp";
        };
        String safeName = fileName == null ? "image" : fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeName.length() > 80) {
            safeName = safeName.substring(safeName.length() - 80);
        }
        return prefix.replaceAll("/+$", "") + "/" + productId + "/" + UUID.randomUUID() + "-" + safeName + extension;
    }

    private static String normalizeContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).strip();
        if (!SetOfImages.SUPPORTED.contains(normalized)) {
            throw new IllegalArgumentException("content type is not supported");
        }
        return normalized;
    }

    private static final class SetOfImages {
        private static final java.util.Set<String> SUPPORTED = java.util.Set.of("image/jpeg", "image/png", "image/webp");
    }
}
