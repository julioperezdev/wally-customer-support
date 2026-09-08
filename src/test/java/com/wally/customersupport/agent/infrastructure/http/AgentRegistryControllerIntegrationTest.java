package com.wally.customersupport.agent.infrastructure.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wally.customersupport.agent.application.port.out.AgentEvaluationControlPlaneAuthorizer;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Import(AgentRegistryControllerIntegrationTest.RegistryAccessConfiguration.class)
@Testcontainers
class AgentRegistryControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("wcs_registry_contract_test")
            .withUsername("wcs")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void returnsTheSeededBaselineWithoutSensitiveFields() throws Exception {
        mockMvc.perform(get("/internal/agent-registry/agents")
                        .param("agentId", "catalog-specialist")
                        .param("limit", "1")
                        .principal(() -> "synthetic-operator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].agentId").value("catalog-specialist"))
                .andExpect(jsonPath("$[0].versions[0].state").value("APPROVED"))
                .andExpect(jsonPath("$[0].versions[0].systemPromptHash").isString())
                .andExpect(jsonPath("$[0].versions[0].prompt").doesNotExist())
                .andExpect(jsonPath("$[0].versions[0].createdBy").doesNotExist())
                .andExpect(jsonPath("$[0].activations").isArray())
                .andExpect(jsonPath("$[0].activations").isEmpty());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RegistryAccessConfiguration {

        @Bean
        @Primary
        AgentEvaluationControlPlaneAuthorizer registryAuthorizer() {
            return request -> AgentEvaluationControlPlaneAccessService.REGISTRY_READ_CAPABILITY
                    .equals(request.capability());
        }
    }
}
