package com.wally.customersupport.agent.application.port.out;

import java.util.List;

import com.wally.customersupport.agent.domain.model.AgentExecutionTrace;

public interface AgentExecutionTraceRepository {

    AgentExecutionTrace save(AgentExecutionTrace trace);

    List<AgentExecutionTrace> findRecent(String agentId, String useCase, int limit);
}
