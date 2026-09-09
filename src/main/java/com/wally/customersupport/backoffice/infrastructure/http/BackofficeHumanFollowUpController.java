package com.wally.customersupport.backoffice.infrastructure.http;

import java.util.Map;
import java.util.UUID;

import com.wally.customersupport.backoffice.application.model.BackofficeHumanFollowUpAction;
import com.wally.customersupport.backoffice.application.service.BackofficeAccessService;
import com.wally.customersupport.backoffice.application.service.BackofficeHumanFollowUpCommandService;
import com.wally.customersupport.backoffice.application.service.BackofficeHumanFollowUpQueryService;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/backoffice/human-follow-ups")
@RequiredArgsConstructor
@Slf4j
public class BackofficeHumanFollowUpController {

    private final BackofficeAccessService accessService;
    private final BackofficeHumanFollowUpQueryService queryService;
    private final BackofficeHumanFollowUpCommandService commandService;

    @GetMapping
    public ResponseEntity<?> findOpen(@RequestParam(defaultValue = "50") int limit) {
        BackofficeAccessService.Decision decision = accessService.authorize("backoffice.human-follow-up.read");
        if (!decision.authorized()) {
            StructuredEventLog.warn(log, "BACKOFFICE_ACCESS_DENIED", Map.of(
                    "operation", "human_follow_up.list",
                    "reason", decision.reason()));
            return ResponseEntity.status(decision.status()).body(Map.of("code", decision.reason()));
        }
        var result = queryService.findOpen(limit);
        StructuredEventLog.info(log, "BACKOFFICE_HUMAN_FOLLOW_UP_VIEWED", Map.of(
                "operation", "human_follow_up.list",
                "resultCount", result.size()));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<?> claim(
            @PathVariable UUID id,
            @RequestHeader("X-WCS-Actor-Key") String actor) {
        return mutate(id, actor, "human_follow_up.claim", commandService::claim);
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<?> release(
            @PathVariable UUID id,
            @RequestHeader("X-WCS-Actor-Key") String actor) {
        return mutate(id, actor, "human_follow_up.release", commandService::release);
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> resolve(
            @PathVariable UUID id,
            @RequestHeader("X-WCS-Actor-Key") String actor) {
        return mutate(id, actor, "human_follow_up.resolve", commandService::resolve);
    }

    private ResponseEntity<?> mutate(
            UUID id,
            String actor,
            String operation,
            java.util.function.BiFunction<UUID, String, BackofficeHumanFollowUpAction> action) {
        BackofficeAccessService.Decision decision = accessService.authorize("backoffice.human-follow-up.write");
        if (!decision.authorized()) {
            StructuredEventLog.warn(log, "BACKOFFICE_ACCESS_DENIED", Map.of(
                    "operation", operation,
                    "reason", decision.reason()));
            return ResponseEntity.status(decision.status()).body(Map.of("code", decision.reason()));
        }
        try {
            BackofficeHumanFollowUpAction result = action.apply(id, actor);
            StructuredEventLog.info(log, "BACKOFFICE_HUMAN_FOLLOW_UP_CHANGED", Map.of(
                    "operation", operation,
                    "status", result.status()));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_ACTOR"));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(Map.of("code", "FOLLOW_UP_STATE_CONFLICT"));
        }
    }
}
