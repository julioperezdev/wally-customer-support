package com.wally.customersupport.knowledge.application.port.out;

import java.util.List;

import com.wally.customersupport.knowledge.domain.model.KnowledgeChunk;
import com.wally.customersupport.knowledge.domain.model.KnowledgeQuery;

public interface KnowledgeRetriever {

    List<KnowledgeChunk> retrieve(KnowledgeQuery query);
}
