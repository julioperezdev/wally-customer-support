package com.wally.customersupport.agent.infrastructure.http;

import java.security.Principal;

import com.wally.customersupport.agent.application.registry.AgentRegistryMutationResult;
import com.wally.customersupport.agent.application.service.AgentRegistryCommandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Closed-by-default HTTP facade for agent authoring and lifecycle commands. */
@RestController
@RequestMapping("/internal/agent-registry/agents")
public class AgentRegistryCommandController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final AgentRegistryCommandService commandService;

    public AgentRegistryCommandController(AgentRegistryCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping("/{agentId}/versions")
    public ResponseEntity<AgentRegistryMutationHttpResponse> createDraft(
            Principal principal,
            @PathVariable String agentId,
            @RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody AgentVersionDraftHttpRequest request) {
        requireIdempotencyKey(idempotencyKey);
        return response(commandService.createDraft(
                request.toCommand(agentId), actor(principal), idempotencyKey));
    }

    @PostMapping("/{agentId}/versions/{version}/clone")
    public ResponseEntity<AgentRegistryMutationHttpResponse> cloneVersion(
            Principal principal,
            @PathVariable String agentId,
            @PathVariable int version,
            @RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        return response(commandService.cloneVersion(
                agentId, version, actor(principal), idempotencyKey));
    }

    @PostMapping("/{agentId}/versions/{version}/lifecycle")
    public ResponseEntity<AgentRegistryMutationHttpResponse> transitionLifecycle(
            Principal principal,
            @PathVariable String agentId,
            @PathVariable int version,
            @RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody AgentLifecycleTransitionHttpRequest request) {
        requireIdempotencyKey(idempotencyKey);
        return response(commandService.transition(
                request.toCommand(agentId, version), actor(principal), idempotencyKey));
    }

    private static String actor(Principal principal) {
        return principal == null ? null : principal.getName();
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(IDEMPOTENCY_HEADER + " must not be blank");
        }
    }

    private static ResponseEntity<AgentRegistryMutationHttpResponse> response(
            AgentRegistryMutationResult result) {
        AgentRegistryMutationHttpResponse response = AgentRegistryMutationHttpResponse.from(result);
        return switch (result.status()) {
            case CREATED -> ResponseEntity.status(HttpStatus.CREATED).body(response);
            case TRANSITIONED -> ResponseEntity.ok(response);
            case ALREADY_PROCESSED, CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            case DENIED, DISABLED -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            case INVALID -> ResponseEntity.unprocessableEntity().body(response);
            case FAILED -> ResponseEntity.internalServerError().body(response);
        };
    }
}
