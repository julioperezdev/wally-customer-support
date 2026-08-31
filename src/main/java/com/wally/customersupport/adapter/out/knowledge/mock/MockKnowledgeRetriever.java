package com.wally.customersupport.adapter.out.knowledge.mock;

import java.util.List;

import com.wally.customersupport.application.port.out.KnowledgeRetriever;
import com.wally.customersupport.domain.model.KnowledgeChunk;
import com.wally.customersupport.domain.model.KnowledgeQuery;
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
