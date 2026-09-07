package com.wally.customersupport.agent.domain.model;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AgentLifecyclePolicy {

    private static final Map<AgentLifecycleState, Set<AgentLifecycleState>> ALLOWED_TRANSITIONS = Map.of(
            AgentLifecycleState.DRAFT, Set.of(AgentLifecycleState.CANDIDATE),
            AgentLifecycleState.CANDIDATE, Set.of(AgentLifecycleState.EVALUATED),
            AgentLifecycleState.EVALUATED, Set.of(AgentLifecycleState.APPROVED),
            AgentLifecycleState.APPROVED, Set.of(AgentLifecycleState.ACTIVE),
            AgentLifecycleState.ACTIVE, EnumSet.of(AgentLifecycleState.DEPRECATED, AgentLifecycleState.ROLLED_BACK),
            AgentLifecycleState.DEPRECATED, Set.of(AgentLifecycleState.ROLLED_BACK),
            AgentLifecycleState.ROLLED_BACK, Set.of());

    public AgentVersion transition(
            AgentVersion version,
            AgentLifecycleState target,
            String actor,
            Instant transitionAt) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(target, "target");
        if (!ALLOWED_TRANSITIONS.get(version.state()).contains(target)) {
            throw new IllegalStateException(
                    "Invalid agent lifecycle transition from " + version.state() + " to " + target);
        }
        return AgentVersion.withLifecycle(version, target, actor, transitionAt);
    }
}
