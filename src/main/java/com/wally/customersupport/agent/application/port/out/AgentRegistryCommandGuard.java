package com.wally.customersupport.agent.application.port.out;

/** Durable idempotency boundary for agent registry authoring commands. */
public interface AgentRegistryCommandGuard {

    boolean tryAcquire(String idempotencyKey);
}
