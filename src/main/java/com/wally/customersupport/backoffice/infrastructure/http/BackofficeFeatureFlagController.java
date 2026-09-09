package com.wally.customersupport.backoffice.infrastructure.http;

import java.security.Principal;
import java.util.Map;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import com.wally.customersupport.featureflag.application.FeatureFlagDocument;
import com.wally.customersupport.featureflag.application.FeatureFlagSnapshotView;
import com.wally.customersupport.featureflag.application.FeatureFlagValidationException;
import com.wally.customersupport.featureflag.application.service.FeatureFlagRuntimeService;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Protected backoffice facade for the hot business-flag control plane. */
@RestController
@RequestMapping("/internal/backoffice/feature-flags")
@RequiredArgsConstructor
@Slf4j
public class BackofficeFeatureFlagController {

    private final AgentEvaluationControlPlaneAccessService accessService;
    private final FeatureFlagRuntimeService runtime;

    @GetMapping
    public ResponseEntity<?> view(Principal principal) {
        AgentEvaluationControlPlaneAccessDecision decision = accessService.authorizeFeatureFlags(actorId(principal));
        if (!decision.authorized()) return forbidden("feature_flags.read", decision);
        return ResponseEntity.ok(runtime.view());
    }

    @PostMapping("/publish")
    public ResponseEntity<?> publish(Principal principal, @RequestBody FeatureFlagDocument document) {
        AgentEvaluationControlPlaneAccessDecision decision = accessService.authorizeFeatureFlagsWrite(actorId(principal));
        if (!decision.authorized()) return forbidden("feature_flags.publish", decision);
        try {
            FeatureFlagSnapshotView result = runtime.publish(document, actorId(principal));
            StructuredEventLog.info(log, "BACKOFFICE_FEATURE_FLAGS_PUBLISHED", Map.of(
                    "operation", "feature_flags.publish",
                    "effectiveVersion", result.effectiveVersion(),
                    "flagCount", result.flags().size()));
            return ResponseEntity.ok(result);
        } catch (FeatureFlagValidationException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "INVALID_FEATURE_FLAG_CONFIGURATION",
                    "message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "code", "FEATURE_FLAG_PUBLICATION_UNAVAILABLE",
                    "message", exception.getMessage()));
        }
    }

    @PostMapping("/rollback")
    public ResponseEntity<?> rollback(Principal principal) {
        AgentEvaluationControlPlaneAccessDecision decision = accessService.authorizeFeatureFlagsWrite(actorId(principal));
        if (!decision.authorized()) return forbidden("feature_flags.rollback", decision);
        try {
            return ResponseEntity.ok(runtime.rollback(actorId(principal)));
        } catch (FeatureFlagValidationException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "FEATURE_FLAG_ROLLBACK_UNAVAILABLE",
                    "message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "code", "FEATURE_FLAG_ROLLBACK_FAILED",
                    "message", exception.getMessage()));
        }
    }

    private ResponseEntity<Map<String, String>> forbidden(
            String operation,
            AgentEvaluationControlPlaneAccessDecision decision) {
        StructuredEventLog.warn(log, "BACKOFFICE_FEATURE_FLAGS_ACCESS_DENIED", Map.of(
                "operation", operation,
                "capability", decision.capability() == null ? "unknown" : decision.capability(),
                "reason", decision.reason().name()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "ACCESS_DENIED"));
    }

    private static String actorId(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
