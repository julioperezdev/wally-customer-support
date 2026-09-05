package com.wally.customersupport.knowledge.infrastructure.ai.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.wally.customersupport.knowledge.domain.model.KnowledgeChunk;
import com.wally.customersupport.knowledge.domain.model.KnowledgeQuery;
import com.wally.customersupport.shared.infrastructure.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;

class BedrockKnowledgeRetrieverTest {

    @Test
    void retrievesAndMapsOnlyTextualKnowledgeResults() {
        BedrockAgentRuntimeClient client = org.mockito.Mockito.mock(BedrockAgentRuntimeClient.class);
        when(client.retrieve(any(RetrieveRequest.class))).thenReturn(RetrieveResponse.builder()
                .retrievalResults(
                        KnowledgeBaseRetrievalResult.builder()
                                .content(RetrievalResultContent.builder().text("Horario demo").build())
                                .score(0.91d)
                                .build(),
                        KnowledgeBaseRetrievalResult.builder().score(0.12d).build())
                .build());

        BedrockKnowledgeRetriever retriever = new BedrockKnowledgeRetriever(
                client,
                new RagProperties("bedrock-kb", 5, "kb-demo", "us-east-1"));

        List<KnowledgeChunk> result = retriever.retrieve(
                new KnowledgeQuery("¿Cuál es el horario?", UUID.randomUUID(), 5));

        assertEquals(1, result.size());
        assertEquals("Horario demo", result.getFirst().content());
        assertEquals(0.91d, result.getFirst().score());
        assertEquals("bedrock-knowledge-base", result.getFirst().sourceId());

        ArgumentCaptor<RetrieveRequest> requestCaptor = ArgumentCaptor.forClass(RetrieveRequest.class);
        verify(client).retrieve(requestCaptor.capture());
        assertEquals("kb-demo", requestCaptor.getValue().knowledgeBaseId());
        assertEquals("¿Cuál es el horario?", requestCaptor.getValue().retrievalQuery().text());
        assertEquals(5, requestCaptor.getValue().retrievalConfiguration()
                .vectorSearchConfiguration().numberOfResults());
    }

    @Test
    void returnsEmptyForBlankQueriesWithoutCallingBedrock() {
        BedrockAgentRuntimeClient client = org.mockito.Mockito.mock(BedrockAgentRuntimeClient.class);
        BedrockKnowledgeRetriever retriever = new BedrockKnowledgeRetriever(
                client,
                new RagProperties("bedrock-kb", 5, "kb-demo", "us-east-1"));

        List<KnowledgeChunk> result = retriever.retrieve(
                new KnowledgeQuery(" ", UUID.randomUUID(), 5));

        assertTrue(result.isEmpty());
        org.mockito.Mockito.verifyNoInteractions(client);
    }
}
