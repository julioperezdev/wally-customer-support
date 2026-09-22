package com.wally.customersupport.agent.application.service;

import java.util.List;

import com.wally.customersupport.agent.application.port.out.AgentExecutionTraceRepository;
import com.wally.customersupport.agent.application.port.out.AgentRegistryAuditRepository;
import com.wally.customersupport.agent.application.registry.AgentExecutionTraceView;
import com.wally.customersupport.agent.application.registry.AgentRegistryAuditView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bounded, sanitized read model for control-plane audit and runtime evidence. */
@Service
@RequiredArgsConstructor
public class AgentRegistryEvidenceQueryService {

    private final AgentRegistryAuditRepository auditRepository;
    private final AgentExecutionTraceRepository traceRepository;

    @Transactional(readOnly = true)
    public List<AgentRegistryAuditView> audit(String agentId, int limit) {
        return auditRepository.findRecent(agentId, limit).stream()
                .map(event -> new AgentRegistryAuditView(
                        event.operation(), event.agentId(), event.agentVersion(), event.previousState(),
                        event.resultingState(), event.environment(), event.channel(), event.useCase(),
                        event.actorId(), event.reason(), event.occurredAt(), event.baselineEvaluationRunId(),
                        event.candidateEvaluationRunId(), event.evaluationDatasetVersion(),
                        event.evaluationAssessmentOutcome()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AgentExecutionTraceView> executions(String agentId, String useCase, int limit) {
        return traceRepository.findRecent(agentId, useCase, limit).stream()
                .map(trace -> new AgentExecutionTraceView(
                        trace.traceId(), trace.correlationId(), trace.actorKey(), trace.agentId(),
                        trace.agentVersion(), trace.environment(), trace.channel(), trace.useCase(),
                        trace.outcome(), trace.resolutionStatus(), trace.provider(), trace.modelId(),
                        trace.durationMs(), trace.inputTokens(), trace.outputTokens(),
                        trace.estimatedCostUsd(), trace.errorType(), trace.executedAt()))
                .toList();
    }
}
