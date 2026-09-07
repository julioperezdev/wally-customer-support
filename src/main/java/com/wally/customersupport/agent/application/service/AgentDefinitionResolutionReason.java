package com.wally.customersupport.agent.application.service;

public enum AgentDefinitionResolutionReason {
    ACTIVE,
    NOT_CONFIGURED,
    DISABLED,
    KILL_SWITCH,
    REGISTRY_UNAVAILABLE,
    VERSION_NOT_FOUND,
    VERSION_MISMATCH,
    VERSION_NOT_PUBLISHABLE,
    INVALID_DEFINITION
}
