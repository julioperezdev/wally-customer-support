package com.wally.customersupport.agent.application.service;

import java.time.Instant;
import java.util.Objects;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionDecision;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionPolicy;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionStatus;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRun;
import org.springframework.stereotype.Service;

/** Evaluates retention without deleting, archiving or mutating evaluation data. */
@Service
public class AgentEvaluationRetentionPolicyService {

    public AgentEvaluationRetentionDecision evaluate(
            AgentEvaluationRun run,
            AgentEvaluationRetentionPolicy policy,
            Instant evaluatedAt) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(policy, "policy");
        Instant evaluationInstant = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Instant expiresAt = policy.expiresAt(run.completedAt());
        AgentEvaluationRetentionStatus status = evaluationInstant.isBefore(expiresAt)
                ? AgentEvaluationRetentionStatus.ACTIVE
                : AgentEvaluationRetentionStatus.EXPIRED;
        return new AgentEvaluationRetentionDecision(run.runId(), run.completedAt(), expiresAt, status);
    }
}
