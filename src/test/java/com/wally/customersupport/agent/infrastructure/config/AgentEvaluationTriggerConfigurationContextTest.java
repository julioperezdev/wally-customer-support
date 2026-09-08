package com.wally.customersupport.agent.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerAuthorizationReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerAuthorizationStatus;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerRequest;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationTriggerAuthorizer;
import com.wally.customersupport.agent.application.service.AgentEvaluationTriggerAuthorizationService;
import com.wally.customersupport.agent.application.service.AgentEvaluationTriggerExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AgentEvaluationTriggerConfigurationContextTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("wcs_test")
            .withUsername("wcs")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("wcs.agent-evaluation.authorization.allowed-environment", () -> "test");
    }

    @Autowired
    private AgentEvaluationTriggerAuthorizationService authorizationService;

    @Autowired
    private AgentEvaluationTriggerExecutionService executionService;

    @Autowired
    private AgentEvaluationTriggerAuthorizer authorizer;

    @Test
    void composesInternalTriggerAndDeniesWhenNoAuthorizerIsConfigured() {
        AgentEvaluationTriggerRequest request = new AgentEvaluationTriggerRequest(
                "context-test-actor",
                "test",
                AgentEvaluationTriggerAuthorizationService.EVALUATION_EXECUTE_CAPABILITY,
                "context-test-idempotency-key");

        var decision = authorizationService.authorize(request);

        assertThat(executionService).isNotNull();
        assertThat(authorizer).isNotNull();
        assertThat(decision.status()).isEqualTo(AgentEvaluationTriggerAuthorizationStatus.DENIED);
        assertThat(decision.reason()).isEqualTo(AgentEvaluationTriggerAuthorizationReason.AUTHORIZER_DENIED);
    }
}
