package com.wally.customersupport.agent.infrastructure.shadow;

import java.util.LinkedHashMap;
import java.util.Map;

import com.wally.customersupport.agent.application.port.out.AgentTrafficComparisonEventPublisher;
import com.wally.customersupport.agent.application.shadow.AgentTrafficComparisonEvent;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Emits comparison metadata as one-line JSON without prompts or responses. */
@Component
@Slf4j
public class StructuredAgentTrafficComparisonEventPublisher
        implements AgentTrafficComparisonEventPublisher {

    @Override
    public void publish(AgentTrafficComparisonEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("comparisonRequestId", event.requestId());
        fields.put("pseudonymizedConversationId", event.pseudonymizedConversationId());
        fields.put("channel", event.channel());
        fields.put("useCase", event.useCase());
        fields.put("agentId", event.agentId());
        fields.put("agentVersion", event.agentVersion());
        fields.put("modelProvider", event.modelProvider());
        fields.put("model", event.modelId());
        fields.put("mode", event.mode().name());
        fields.put("outcome", event.outcome());
        fields.put("latencyMs", event.latencyMs());
        fields.put("inputTokens", event.inputTokens());
        fields.put("outputTokens", event.outputTokens());
        fields.put("totalTokens", event.totalTokens());
        fields.put("estimatedCostUsd", event.estimatedCostUsd());
        fields.put("fallbackReason", event.fallbackReason());
        fields.put("candidateResponsePublished", event.candidateResponsePublished());
        StructuredEventLog.info(log, "AGENT_TRAFFIC_COMPARISON_RECORDED", fields);
    }
}
