package com.wally.customersupport.agent.application.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.agent.application.port.out.AgentExecutionTraceRepository;
import com.wally.customersupport.agent.domain.model.AgentExecutionTrace;
import com.wally.customersupport.shared.infrastructure.observability.ActorKeyGenerator;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists bounded execution evidence without storing message content. */
@Service
@Slf4j
public class AgentExecutionTraceRecorder {

    private final AgentExecutionTraceRepository repository;
    private final ActorKeyGenerator actorKeyGenerator;

    @Autowired
    public AgentExecutionTraceRecorder(
            AgentExecutionTraceRepository repository,
            ActorKeyGenerator actorKeyGenerator) {
        this.repository = repository;
        this.actorKeyGenerator = actorKeyGenerator;
    }

    /** Compatibility no-op for pure orchestrator unit tests. */
    public AgentExecutionTraceRecorder() {
        this.repository = null;
        this.actorKeyGenerator = null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            ConversationContext context,
            ConversationExecutionResult result,
            AgentRuntimeDefinition definition,
            String environment,
            long durationMs) {
        try {
            if (repository == null) {
                return;
            }
            Channel channel = context == null ? null : context.channel();
            AgentExecutionTrace trace = new AgentExecutionTrace(
                    UUID.randomUUID(),
                    context == null || context.conversationId() == null
                            ? null : context.conversationId().toString(),
                    context == null ? null : actorKeyGenerator.generate(
                            channel,
                            context.externalCustomerId()).orElse(null),
                    definition == null ? null : definition.agentId(),
                    definition == null ? null : definition.agentVersion(),
                    environment,
                    channel == null ? "unknown" : channel.name().toLowerCase(),
                    result.useCase(),
                    result.outcome(),
                    definition == null ? "FALLBACK" : "ACTIVE",
                    definition == null ? null : definition.modelProvider(),
                    definition == null ? null : definition.modelId(),
                    durationMs,
                    null,
                    null,
                    (BigDecimal) null,
                    result.fallbackReason(),
                    Instant.now());
            repository.save(trace);
        } catch (RuntimeException exception) {
            // Trace persistence must never interrupt a customer response.
            log.warn("event=AGENT_EXECUTION_TRACE_PERSIST_FAILED errorType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
