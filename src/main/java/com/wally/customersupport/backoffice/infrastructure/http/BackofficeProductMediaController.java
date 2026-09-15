package com.wally.customersupport.backoffice.infrastructure.http;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

import com.wally.customersupport.backoffice.application.model.BackofficeImageUpload;
import com.wally.customersupport.backoffice.application.model.BackofficeImageView;
import com.wally.customersupport.backoffice.application.service.BackofficeAccessService;
import com.wally.customersupport.backoffice.application.service.BackofficeProductMediaService;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/backoffice/catalog/products/{productId}/image")
@RequiredArgsConstructor
@Slf4j
public class BackofficeProductMediaController {

    private final BackofficeAccessService accessService;
    private final BackofficeProductMediaService mediaService;

    @GetMapping("/view-url")
    public ResponseEntity<?> requestView(
            Principal principal,
            @PathVariable UUID productId) {
        BackofficeAccessService.Decision decision = accessService.authorize(
                "backoffice.catalog.read", actorId(principal));
        if (!decision.authorized()) {
            return ResponseEntity.status(decision.status()).body(Map.of("code", decision.reason()));
        }
        try {
            BackofficeImageView result = mediaService.requestView(productId);
            StructuredEventLog.info(log, "BACKOFFICE_CATALOG_MEDIA_VIEW_REQUESTED", Map.of(
                    "operation", "catalog.image.view",
                    "productId", productId.toString()));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(404).body(Map.of("code", "IMAGE_NOT_FOUND"));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(Map.of("code", "MEDIA_NOT_AVAILABLE"));
        }
    }

    @PostMapping("/upload-url")
    public ResponseEntity<?> requestUpload(
            Principal principal,
            @PathVariable UUID productId,
            @RequestBody ImageUploadRequest request) {
        return execute(principal, "catalog.image.request", () -> mediaService.requestUpload(
                productId, request.fileName(), request.contentType(), request.contentLength()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmUpload(
            Principal principal,
            @PathVariable UUID productId,
            @RequestBody ImageConfirmationRequest request) {
        return execute(principal, "catalog.image.confirm", () -> {
            mediaService.confirmUpload(productId, request.objectKey());
            return Map.of("status", "CONFIRMED");
        });
    }

    private ResponseEntity<?> execute(
            Principal principal,
            String operation,
            java.util.function.Supplier<Object> command) {
        BackofficeAccessService.Decision decision = accessService.authorize(
                "backoffice.catalog.media.write", actorId(principal));
        if (!decision.authorized()) {
            return ResponseEntity.status(decision.status()).body(Map.of("code", decision.reason()));
        }
        try {
            Object result = command.get();
            StructuredEventLog.info(log, "BACKOFFICE_CATALOG_MEDIA_CHANGED", Map.of("operation", operation));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_IMAGE_REQUEST"));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(Map.of("code", "MEDIA_NOT_AVAILABLE"));
        }
    }

    public record ImageUploadRequest(String fileName, String contentType, long contentLength) {
    }

    public record ImageConfirmationRequest(String objectKey) {
    }

    private static String actorId(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
