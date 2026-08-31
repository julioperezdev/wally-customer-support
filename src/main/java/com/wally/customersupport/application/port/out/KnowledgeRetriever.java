package com.wally.customersupport.application.port.out;

import java.util.List;

import com.wally.customersupport.domain.model.KnowledgeChunk;
import com.wally.customersupport.domain.model.KnowledgeQuery;

public interface KnowledgeRetriever {

    List<KnowledgeChunk> retrieve(KnowledgeQuery query);
}
