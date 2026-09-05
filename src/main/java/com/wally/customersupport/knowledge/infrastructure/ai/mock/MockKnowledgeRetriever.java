package com.wally.customersupport.knowledge.infrastructure.ai.mock;

import java.util.List;

import com.wally.customersupport.knowledge.application.port.out.KnowledgeRetriever;
import com.wally.customersupport.knowledge.domain.model.KnowledgeChunk;
import com.wally.customersupport.knowledge.domain.model.KnowledgeQuery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wcs.rag.provider", havingValue = "mock", matchIfMissing = true)
public class MockKnowledgeRetriever implements KnowledgeRetriever {

    @Override
    public List<KnowledgeChunk> retrieve(KnowledgeQuery query) {
        return List.of();
    }
}
