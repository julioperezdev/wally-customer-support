package com.wally.customersupport.conversation.application.tool;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.wally.customersupport.knowledge.application.port.out.KnowledgeRetriever;
import com.wally.customersupport.knowledge.domain.model.KnowledgeChunk;
import com.wally.customersupport.knowledge.domain.model.KnowledgeQuery;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Typed adapter for grounded retrieval.
 *
 * <p>The tool returns retrieval metadata rather than raw document content. The
 * application layer remains responsible for deciding how the evidence is
 * incorporated into a response, and the provider remains behind the existing
 * {@link KnowledgeRetriever} port.</p>
 */
@Component
@Slf4j
public final class KnowledgeRetrieveTool implements WcsTool<KnowledgeRetrieveTool.Input, KnowledgeRetrieveTool.Result> {

    public static final String NAME = WcsToolContractCatalog.KNOWLEDGE_RETRIEVE;
    private static final int MAX_RESULTS = 20;
    public static final WcsToolDescriptor DESCRIPTOR = WcsToolContractCatalog.find(NAME).orElseThrow();

    private final KnowledgeRetriever knowledgeRetriever;

    public KnowledgeRetrieveTool(KnowledgeRetriever knowledgeRetriever) {
        this.knowledgeRetriever = Objects.requireNonNull(knowledgeRetriever, "knowledgeRetriever");
    }

    @Override
    public WcsToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public Result execute(Input input) {
        Objects.requireNonNull(input, "input");
        try {
            List<KnowledgeChunk> chunks = knowledgeRetriever.retrieve(new KnowledgeQuery(
                    input.query(), input.conversationId(), input.maxResults()));
            List<KnowledgeChunk> safeChunks = chunks == null ? List.of() : chunks;
            Result result = Result.from(safeChunks);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("tool", NAME);
            fields.put("status", result.status().name());
            fields.put("evidenceCount", result.evidenceCount());
            if (result.groundingScore() != null) {
                fields.put("groundingScore", result.groundingScore());
            }
            StructuredEventLog.info(log, "WCS_TOOL_EXECUTED", fields);
            return result;
        } catch (RuntimeException exception) {
            StructuredEventLog.warn(log, "WCS_TOOL_FAILED", java.util.Map.of(
                    "tool", NAME,
                    "errorType", exception.getClass().getSimpleName()));
            return new Result(Status.ERROR, 0, null);
        }
    }

    public record Input(String query, UUID conversationId, int maxResults, String topic) {

        public Input {
            query = required(query, "query", 1000);
            if (maxResults < 1 || maxResults > MAX_RESULTS) {
                throw new IllegalArgumentException("maxResults must be between 1 and " + MAX_RESULTS);
            }
            topic = optional(topic, "topic", 64);
        }
    }

    public enum Status {
        GROUNDED,
        NO_EVIDENCE,
        ERROR
    }

    public record Result(Status status, int evidenceCount, Double groundingScore) {

        public Result {
            status = Objects.requireNonNull(status, "status");
            if (evidenceCount < 0) {
                throw new IllegalArgumentException("evidenceCount must not be negative");
            }
            if (groundingScore != null && (!Double.isFinite(groundingScore)
                    || groundingScore < 0 || groundingScore > 1)) {
                throw new IllegalArgumentException("groundingScore must be between 0 and 1");
            }
        }

        private static Result from(List<KnowledgeChunk> chunks) {
            if (chunks.isEmpty()) {
                return new Result(Status.NO_EVIDENCE, 0, null);
            }
            double average = chunks.stream()
                    .mapToDouble(KnowledgeChunk::score)
                    .map(KnowledgeRetrieveTool::boundedScore)
                    .average()
                    .orElse(0);
            return new Result(Status.GROUNDED, chunks.size(), average);
        }
    }

    private static double boundedScore(double score) {
        if (!Double.isFinite(score)) {
            return 0;
        }
        return Math.max(0, Math.min(1, score));
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isBlank() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optional(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(value, field, maxLength);
    }
}
