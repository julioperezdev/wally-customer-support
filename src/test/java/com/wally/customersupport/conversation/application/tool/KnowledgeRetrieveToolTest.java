package com.wally.customersupport.conversation.application.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.wally.customersupport.knowledge.application.port.out.KnowledgeRetriever;
import com.wally.customersupport.knowledge.domain.model.KnowledgeChunk;
import com.wally.customersupport.knowledge.domain.model.KnowledgeQuery;
import org.junit.jupiter.api.Test;

class KnowledgeRetrieveToolTest {

    @Test
    void returnsGroundingMetadataWithoutExposingRetrievedContent() {
        KnowledgeRetriever retriever = mock(KnowledgeRetriever.class);
        when(retriever.retrieve(any(KnowledgeQuery.class))).thenReturn(List.of(
                new KnowledgeChunk("Dirección privada que no debe salir del tool", 0.9, "s3-1"),
                new KnowledgeChunk("Horario", 0.7, "s3-2")));
        KnowledgeRetrieveTool tool = new KnowledgeRetrieveTool(retriever);

        KnowledgeRetrieveTool.Result result = tool.execute(new KnowledgeRetrieveTool.Input(
                "¿Dónde están ubicados?", UUID.randomUUID(), 5, "location"));

        assertThat(result.status()).isEqualTo(KnowledgeRetrieveTool.Status.GROUNDED);
        assertThat(result.evidenceCount()).isEqualTo(2);
        assertThat(result.groundingScore()).isEqualTo(0.8);
        assertThat(result.toString()).doesNotContain("Dirección privada");
        verify(retriever).retrieve(any(KnowledgeQuery.class));
    }

    @Test
    void returnsNoEvidenceForEmptyRetrieval() {
        KnowledgeRetriever retriever = mock(KnowledgeRetriever.class);
        when(retriever.retrieve(any(KnowledgeQuery.class))).thenReturn(List.of());
        KnowledgeRetrieveTool tool = new KnowledgeRetrieveTool(retriever);

        KnowledgeRetrieveTool.Result result = tool.execute(new KnowledgeRetrieveTool.Input(
                "¿Cuál es la política de cambios?", null, 5, "returns"));

        assertThat(result).isEqualTo(new KnowledgeRetrieveTool.Result(
                KnowledgeRetrieveTool.Status.NO_EVIDENCE, 0, null));
    }

    @Test
    void convertsProviderFailureToBoundedErrorResult() {
        KnowledgeRetriever retriever = mock(KnowledgeRetriever.class);
        when(retriever.retrieve(any(KnowledgeQuery.class))).thenThrow(new IllegalStateException("provider down"));
        KnowledgeRetrieveTool tool = new KnowledgeRetrieveTool(retriever);

        KnowledgeRetrieveTool.Result result = tool.execute(new KnowledgeRetrieveTool.Input(
                "horarios", null, 1, null));

        assertThat(result).isEqualTo(new KnowledgeRetrieveTool.Result(
                KnowledgeRetrieveTool.Status.ERROR, 0, null));
    }

    @Test
    void validatesQueryAndResultBoundsBeforeCallingProvider() {
        KnowledgeRetriever retriever = mock(KnowledgeRetriever.class);

        assertThatThrownBy(() -> new KnowledgeRetrieveTool.Input(" ", null, 5, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KnowledgeRetrieveTool.Input("query", null, 21, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(retriever);
    }
}
