package com.wally.customersupport.agent.infrastructure.http;

import java.security.Principal;

import com.wally.customersupport.agent.application.activation.AgentActivationMutationResult;
import com.wally.customersupport.agent.application.service.AgentActivationCommandService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Closed-by-default HTTP facade for auditable agent activation actions. */
@RestController
@RequestMapping("/internal/agent-registry/activations")
public class AgentActivationController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final AgentActivationCommandService commandService;
    private final boolean writeEnabled;

    public AgentActivationController(
            AgentActivationCommandService commandService,
            @Value("${wcs.agent-registry.activation-write-enabled:false}") boolean writeEnabled) {
        this.commandService = commandService;
        this.writeEnabled = writeEnabled;
    }

    @PostMapping
    public ResponseEntity<AgentActivationHttpResponse> activate(
            Principal principal,
            @RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody AgentActivationHttpRequest request) {
        requireIdempotencyKey(idempotencyKey);
        return response(commandService.activate(
                request.toCommand(),
                actor(principal),
                idempotencyKey,
                writeEnabled));
    }

    @PostMapping("/kill-switch")
    public ResponseEntity<AgentActivationHttpResponse> killSwitch(
            Principal principal,
            @RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody AgentActivationActionHttpRequest request) {
        requireIdempotencyKey(idempotencyKey);
        return response(commandService.killSwitch(
                request.toCommand(),
                actor(principal),
                idempotencyKey,
                writeEnabled));
    }

    @PostMapping("/rollback")
    public ResponseEntity<AgentActivationHttpResponse> rollback(
            Principal principal,
            @RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody AgentActivationActionHttpRequest request) {
        requireIdempotencyKey(idempotencyKey);
        return response(commandService.rollback(
                request.toCommand(),
                actor(principal),
                idempotencyKey,
                writeEnabled));
    }

    private static String actor(Principal principal) {
        return principal == null ? null : principal.getName();
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(IDEMPOTENCY_HEADER + " must not be blank");
        }
    }

    private static ResponseEntity<AgentActivationHttpResponse> response(
            AgentActivationMutationResult result) {
        AgentActivationHttpResponse response = AgentActivationHttpResponse.from(result);
        return switch (result.status()) {
            case ACTIVATED, KILL_SWITCHED, ROLLED_BACK -> ResponseEntity.ok(response);
            case ALREADY_PROCESSED -> ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            case DENIED, DISABLED -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            case INVALID -> ResponseEntity.unprocessableEntity().body(response);
            case FAILED -> ResponseEntity.internalServerError().body(response);
        };
    }
}
