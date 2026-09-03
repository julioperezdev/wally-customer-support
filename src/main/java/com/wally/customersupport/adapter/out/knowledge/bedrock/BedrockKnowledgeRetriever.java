package com.wally.customersupport.adapter.out.knowledge.bedrock;

import java.util.List;
import java.util.Objects;

import com.wally.customersupport.application.port.out.KnowledgeRetriever;
import com.wally.customersupport.domain.model.KnowledgeChunk;
import com.wally.customersupport.domain.model.KnowledgeQuery;
import com.wally.customersupport.infrastructure.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;

/** Retrieves approved static knowledge without exposing Bedrock types to the application layer. */
public final class BedrockKnowledgeRetriever implements KnowledgeRetriever {

    private static final Logger log = LoggerFactory.getLogger(BedrockKnowledgeRetriever.class);

    private final BedrockAgentRuntimeClient client;
    private final RagProperties properties;

    public BedrockKnowledgeRetriever(BedrockAgentRuntimeClient client, RagProperties properties) {
        this.client = Objects.requireNonNull(client);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public List<KnowledgeChunk> retrieve(KnowledgeQuery query) {
        if (query == null || query.text() == null || query.text().isBlank()) {
            return List.of();
        }

        long startedAt = System.nanoTime();
        try {
            RetrieveResponse response = client.retrieve(RetrieveRequest.builder()
                    .knowledgeBaseId(properties.requiredKnowledgeBaseId())
                    .retrievalQuery(KnowledgeBaseQuery.builder()
                            .text(limit(query.text(), 2_000))
                            .build())
                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                    .numberOfResults(normalizeMaxResults(query.maxResults()))
                                    .build())
                            .build())
                    .build());

            List<KnowledgeChunk> chunks = response.retrievalResults().stream()
                    .map(BedrockKnowledgeRetriever::toKnowledgeChunk)
                    .filter(Objects::nonNull)
                    .toList();
            log.info("Bedrock Knowledge Base retrieval completed: resultCount={}, latencyMs={}",
                    chunks.size(), elapsedMillis(startedAt));
            return chunks;
        } catch (RuntimeException exception) {
            log.warn("Bedrock Knowledge Base retrieval failed: errorType={}, latencyMs={}",
                    exception.getClass().getSimpleName(), elapsedMillis(startedAt));
            throw exception;
        }
    }

    private static KnowledgeChunk toKnowledgeChunk(KnowledgeBaseRetrievalResult result) {
        RetrievalResultContent content = result.content();
        if (content == null || content.text() == null || content.text().isBlank()) {
            return null;
        }
        String sourceId = result.location() == null || result.location().s3Location() == null
                ? "bedrock-knowledge-base"
                : result.location().s3Location().uri();
        double score = result.score() == null ? 0.0d : result.score();
        return new KnowledgeChunk(content.text(), score, sourceId);
    }

    private static int normalizeMaxResults(int maxResults) {
        return Math.max(1, Math.min(maxResults, 20));
    }

    private static String limit(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
