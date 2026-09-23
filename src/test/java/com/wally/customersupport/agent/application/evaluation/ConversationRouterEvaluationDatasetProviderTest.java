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
    }
}
