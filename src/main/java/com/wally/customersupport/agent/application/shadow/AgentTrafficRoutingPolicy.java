package com.wally.customersupport.agent.application.shadow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Component;

/**
 * Deterministic, provider-neutral routing policy for a future shadow/canary
 * runner. It deliberately has no channel or outbound dependency.
 */
@Component
public class AgentTrafficRoutingPolicy {

    public AgentTrafficRoutingDecision decide(AgentTrafficRoutingRequest request) {
        return switch (request.mode()) {
            case SHADOW -> shadowDecision(request);
            case ACTIVE -> new AgentTrafficRoutingDecision(
                    AgentTrafficMode.ACTIVE, false, false, true, "ACTIVE_RUNTIME_REMAINS_SOURCE_OF_TRUTH");
            case CANARY -> canaryDecision(request);
        };
    }

    private static AgentTrafficRoutingDecision shadowDecision(AgentTrafficRoutingRequest request) {
        boolean selected = bucket(request.pseudonymizedConversationKey()) < request.rolloutPercentage();
        return new AgentTrafficRoutingDecision(
                AgentTrafficMode.SHADOW,
                selected,
                false,
                true,
                selected ? "SHADOW_BUCKET_SELECTED" : "SHADOW_BUCKET_NOT_SELECTED");
    }

    private static AgentTrafficRoutingDecision canaryDecision(AgentTrafficRoutingRequest request) {
        int bucket = bucket(request.pseudonymizedConversationKey());
        boolean selected = bucket < request.rolloutPercentage();
        return new AgentTrafficRoutingDecision(
                AgentTrafficMode.CANARY,
                selected,
                selected,
                !selected,
                selected ? "CANARY_BUCKET_SELECTED" : "CANARY_BUCKET_FALLBACK");
    }

    private static int bucket(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            int value = ((digest[0] & 0xff) << 8) | (digest[1] & 0xff);
            return value % 100;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
