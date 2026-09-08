package com.wally.customersupport.agent.application.port.out;

/** Claims an evaluation trigger key exactly once for the lifetime of a trigger flow. */
@FunctionalInterface
public interface AgentEvaluationTriggerExecutionGuard {

    /**
     * Attempts to claim the key before an evaluation is started.
     *
     * <p>The adapter owns the storage and atomicity guarantees. A {@code false}
     * result means that the key was already claimed and the caller must not
     * execute the evaluation again.</p>
     */
    boolean tryAcquire(String idempotencyKey);
}
