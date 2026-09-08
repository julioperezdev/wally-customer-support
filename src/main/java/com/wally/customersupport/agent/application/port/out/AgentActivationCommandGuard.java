package com.wally.customersupport.agent.application.port.out;

/** Claims an activation command without persisting the raw idempotency key. */
@FunctionalInterface
public interface AgentActivationCommandGuard {

    boolean tryAcquire(String idempotencyKey);
}
