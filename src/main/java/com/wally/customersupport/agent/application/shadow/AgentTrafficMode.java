package com.wally.customersupport.agent.application.shadow;

/** Runtime traffic modes used by the migration plan; production remains active-only until enabled. */
public enum AgentTrafficMode {
    SHADOW,
    CANARY,
    ACTIVE
}
