package com.wally.customersupport.agent.application.evaluation;

/** Bounded page request for internal evaluation history queries. */
public record AgentEvaluationHistoryPageRequest(int pageNumber, int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public AgentEvaluationHistoryPageRequest {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must not be negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    public static AgentEvaluationHistoryPageRequest firstPage() {
        return new AgentEvaluationHistoryPageRequest(0, DEFAULT_PAGE_SIZE);
    }
}
