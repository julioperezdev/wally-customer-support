package com.wally.customersupport.agent.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class AgentActivationPolicy {

    public AgentActivation activate(
            AgentVersion version,
            AgentActivationRequest request,
            Integer previousVersion,
            String actor,
            Instant activatedAt) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(request, "request");
        if (!version.canBeActivated()) {
            throw new IllegalStateException("only approved agent versions can be activated");
        }
        if (previousVersion != null && previousVersion == version.version()) {
            throw new IllegalArgumentException("previousVersion must differ from the activated version");
        }
        return new AgentActivation(
                version.agentId(),
                version.version(),
                request.environment(),
                request.channel(),
                request.useCase(),
                request.reason(),
                request.rolloutPercentage(),
                request.enabled(),
                false,
                previousVersion,
                activatedAt,
                actor);
    }

    public AgentActivation killSwitch(AgentActivation activation, String actor, Instant at) {
        return Objects.requireNonNull(activation, "activation").killSwitch(actor, at);
    }

    public AgentActivation rollback(
            AgentActivation activation,
            AgentVersion previous,
            String actor,
            Instant at) {
        return Objects.requireNonNull(activation, "activation").rollbackTo(previous, actor, at);
    }
}
