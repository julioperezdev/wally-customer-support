package com.wally.customersupport.agent.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class ConversationRouterEvaluationDatasetProviderTest {

    private final ConversationRouterEvaluationDatasetProvider provider =
            new ConversationRouterEvaluationDatasetProvider(new ObjectMapper());

    @Test
    void publishesAStableSanitizedSuiteWithConversationalContextAndRouteOracles() {
        assertThat(provider.version()).isEqualTo("conversation-routing-v1");
        assertThat(provider.agentId()).isEqualTo("conversation-router");
        assertThat(provider.scenarios()).hasSize(31)
                .allSatisfy(scenario -> {
                    assertThat(scenario.datasetVersion()).isEqualTo(provider.version());
                    assertThat(scenario.routingContext()).isNotNull();
                    assertThat(scenario.expectedIntent()).isNotBlank();
                    assertThat(scenario.expectedAction()).isNotBlank();
                    assertThat(scenario.expectedEntityTypes()).isNotNull();
                    assertThat(scenario.request()).isNull();
                });

        assertThat(provider.scenarios()).anySatisfy(scenario -> {
            assertThat(scenario.scenarioId()).isEqualTo("size_context_refinement");
            assertThat(scenario.routingContext().latestMessage()).isEqualTo("Quiero la talla M");
            assertThat(scenario.routingContext().recentMessages())
                    .containsExactly("Quiero la talla M", "Busco una remera negra talle M");
        });
        assertThat(provider.scenarios()).allSatisfy(scenario -> assertThat(scenario.expectedQuantity()).isNull());
        assertThat(new AgentEvaluationDatasetCatalog(java.util.List.of(
                provider,
                new ConversationRouterEvaluationDatasetProvider(
                        new ObjectMapper(), ConversationRouterEvaluationDatasetProvider.VERSION_2)))
                .descriptors())
                .extracting(AgentEvaluationDatasetDescriptor::datasetVersion)
                .containsExactly("conversation-routing-v1", "conversation-routing-v2");
    }

    @Test
    void publishesV2WithExplicitFilterAndQuantityExpectationsWithoutChangingV1() {
        var v2 = new ConversationRouterEvaluationDatasetProvider(new ObjectMapper(),
                ConversationRouterEvaluationDatasetProvider.VERSION_2);

        assertThat(v2.version()).isEqualTo("conversation-routing-v2");
        assertThat(v2.agentId()).isEqualTo("conversation-router");
        assertThat(v2.scenarios()).hasSize(34).allSatisfy(scenario -> {
            assertThat(scenario.datasetVersion()).isEqualTo(v2.version());
            assertThat(scenario.expectedIntent()).isNotBlank();
            assertThat(scenario.expectedAction()).isNotBlank();
            assertThat(scenario.expectedEntityTypes()).isNotNull();
            assertThat(scenario.routingContext()).isNotNull();
        });
        assertThat(v2.scenarios()).anySatisfy(scenario -> {
            assertThat(scenario.scenarioId()).isEqualTo("add_quantity_to_cart");
            assertThat(scenario.expectedQuantity()).isEqualTo(2);
            assertThat(scenario.expectedEntityTypes()).containsExactly(
                    "color=negro", "productType=remera", "size=M");
        });
        assertThat(v2.scenarios()).anySatisfy(scenario -> {
            assertThat(scenario.scenarioId()).isEqualTo("price_filter");
            assertThat(scenario.expectedEntityTypes()).containsExactly("maxPrice=70000", "productType=campera");
        });
        assertThat(provider.version()).isEqualTo("conversation-routing-v1");
        assertThat(provider.scenarios()).hasSize(31);
    }

    @Test
    void rejectsUnknownRouterDatasetVersions() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new ConversationRouterEvaluationDatasetProvider(new ObjectMapper(), "conversation-routing-v3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");
    }
}
