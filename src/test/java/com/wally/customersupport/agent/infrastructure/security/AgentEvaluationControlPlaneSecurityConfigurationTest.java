package com.wally.customersupport.agent.infrastructure.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessStatus;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPage;
import com.wally.customersupport.agent.application.service.AgentEvaluationComparisonApplicationService;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import com.wally.customersupport.agent.application.service.AgentEvaluationEvidenceExportApplicationService;
import com.wally.customersupport.agent.application.service.AgentEvaluationHistoryQueryService;
import com.wally.customersupport.agent.infrastructure.http.AgentEvaluationControlPlaneController;
import com.wally.customersupport.agent.infrastructure.http.AgentEvaluationControlPlaneExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AgentEvaluationControlPlaneSecurityConfigurationTest.SecurityTestConfiguration.class)
@TestPropertySource(properties = {
        "wcs.agent-evaluation.control-plane.security.enabled=true",
        "wcs.agent-evaluation.control-plane.security.issuer-uri=https://issuer.example.test",
        "wcs.agent-evaluation.control-plane.security.audience=wcs-control-plane"
})
@WebAppConfiguration
class AgentEvaluationControlPlaneSecurityConfigurationTest {

    private static final String ACTOR = "synthetic-operator";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AgentEvaluationControlPlaneAccessService accessService;

    @Autowired
    private AgentEvaluationHistoryQueryService historyQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void resetMocks() {
        reset(accessService, historyQueryService);
    }

    @Test
    void acceptsAValidJwtWithTheRequiredScope() throws Exception {
        when(accessService.authorize(ACTOR)).thenReturn(authorized());
        when(historyQueryService.search(any(), any())).thenReturn(
                new AgentEvaluationHistoryPage(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/internal/agent-evaluations/runs")
                        .with(jwt()
                                .jwt(token -> token.subject(ACTOR).audience(List.of("wcs-control-plane")))
                                .authorities(new SimpleGrantedAuthority(
                                        "SCOPE_agent-evaluation.read"))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMissingJwtWithUnauthorized() throws Exception {
        mockMvc.perform(get("/internal/agent-evaluations/runs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsJwtWithoutTheRequiredScopeWithForbidden() throws Exception {
        mockMvc.perform(get("/internal/agent-evaluations/runs")
                        .with(jwt().jwt(token -> token.subject(ACTOR))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsReadScopeWhenTryingToExecute() throws Exception {
        mockMvc.perform(post("/internal/agent-evaluations/runs")
                        .with(jwt()
                                .jwt(token -> token.subject(ACTOR).audience(List.of("wcs-control-plane")))
                                .authorities(new SimpleGrantedAuthority(
                                        "SCOPE_agent-evaluation.read")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void keepsEvaluationScopeSeparateFromTheRegistryScope() throws Exception {
        mockMvc.perform(get("/internal/agent-registry/agents")
                        .with(jwt()
                                .jwt(token -> token.subject(ACTOR).audience(List.of("wcs-control-plane")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_agent-evaluation.read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/internal/agent-evaluations/runs")
                        .with(jwt()
                                .jwt(token -> token.subject(ACTOR).audience(List.of("wcs-control-plane")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_agent-registry.read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsRegistryScopeAtTheRegistryBoundary() throws Exception {
        mockMvc.perform(get("/internal/agent-registry/agents")
                        .with(jwt()
                                .jwt(token -> token.subject(ACTOR).audience(List.of("wcs-control-plane")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_agent-registry.read"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void keepsRegistryWriteScopeSeparateFromRegistryReadScope() throws Exception {
        mockMvc.perform(post("/internal/agent-registry/activations")
                        .with(jwt()
                                .jwt(token -> token.subject(ACTOR).audience(List.of("wcs-control-plane")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_agent-registry.read")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/internal/agent-registry/activations")
                        .with(jwt()
                                .jwt(token -> token.subject(ACTOR).audience(List.of("wcs-control-plane")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_agent-registry.write")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void allowsRegistryReadScopeToReachTheNonMutatingPreflightOnly() throws Exception {
        mockMvc.perform(post("/internal/agent-registry/activations/preflight")
                        .with(jwt()
                                .jwt(token -> token.subject(ACTOR).audience(List.of("wcs-control-plane")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_agent-registry.read")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/internal/agent-registry/activations/preflight")
                        .with(jwt()
                                .jwt(token -> token.subject(ACTOR).audience(List.of("wcs-control-plane")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_agent-registry.write")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void letsExecutionScopeReachThePostHandlerWithoutGrantingReadAccess() throws Exception {
        mockMvc.perform(post("/internal/agent-evaluations/runs")
                        .with(jwt()
                                .jwt(token -> token.subject(ACTOR).audience(List.of("wcs-control-plane")))
                                .authorities(new SimpleGrantedAuthority(
                                        "SCOPE_agent-evaluation.execute")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void rejectsExpiredOrWrongAudienceBeforeARequestCanBeAuthorized() {
        OAuth2TokenValidator<Jwt> validator = AgentEvaluationControlPlaneSecurityConfiguration.tokenValidator(
                "https://issuer.example.test", "wcs-control-plane");
        Jwt expired = Jwt.withTokenValue("expired-token")
                .header("alg", "none")
                .subject(ACTOR)
                .issuer("https://issuer.example.test")
                .audience(List.of("wcs-control-plane"))
                .issuedAt(Instant.parse("2026-09-07T00:00:00Z"))
                .expiresAt(Instant.parse("2026-09-07T01:00:00Z"))
                .build();
        Jwt wrongAudience = Jwt.withTokenValue("wrong-audience-token")
                .header("alg", "none")
                .subject(ACTOR)
                .issuer("https://issuer.example.test")
                .audience(List.of("another-service"))
                .issuedAt(Instant.parse("2026-09-08T00:00:00Z"))
                .expiresAt(Instant.parse("2026-09-09T00:00:00Z"))
                .build();

        org.assertj.core.api.Assertions.assertThat(validator.validate(expired).hasErrors()).isTrue();
        org.assertj.core.api.Assertions.assertThat(validator.validate(wrongAudience).hasErrors()).isTrue();
    }

    @Test
    void leavesWebhookRoutesOutsideTheInternalSecurityBoundary() throws Exception {
        mockMvc.perform(get("/webhook/telegram"))
                .andExpect(status().isOk());
    }

    private static AgentEvaluationControlPlaneAccessDecision authorized() {
        return new AgentEvaluationControlPlaneAccessDecision(
                AgentEvaluationControlPlaneAccessStatus.AUTHORIZED,
                ACTOR,
                "prod",
                AgentEvaluationControlPlaneAccessService.EVALUATION_READ_CAPABILITY,
                AgentEvaluationControlPlaneAccessReason.AUTHORIZED);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    static class SecurityTestConfiguration {

        @Bean
        SecurityFilterChain controlPlaneFilterChain(HttpSecurity http) throws Exception {
            return new AgentEvaluationControlPlaneSecurityConfiguration()
                    .agentEvaluationControlPlaneSecurityFilterChain(http);
        }

        @Bean
        SecurityFilterChain publicEndpointsFilterChain(HttpSecurity http) throws Exception {
            return new AgentEvaluationControlPlaneSecurityConfiguration()
                    .publicEndpointsSecurityFilterChain(http);
        }

        @Bean
        AgentEvaluationControlPlaneAccessService accessService() {
            return Mockito.mock(AgentEvaluationControlPlaneAccessService.class);
        }

        @Bean
        AgentEvaluationHistoryQueryService historyQueryService() {
            return Mockito.mock(AgentEvaluationHistoryQueryService.class);
        }

        @Bean
        AgentEvaluationComparisonApplicationService comparisonService() {
            return Mockito.mock(AgentEvaluationComparisonApplicationService.class);
        }

        @Bean
        AgentEvaluationEvidenceExportApplicationService evidenceExportService() {
            return Mockito.mock(AgentEvaluationEvidenceExportApplicationService.class);
        }

        @Bean
        AgentEvaluationControlPlaneController controlPlaneController(
                AgentEvaluationControlPlaneAccessService accessService,
                AgentEvaluationHistoryQueryService historyQueryService,
                AgentEvaluationComparisonApplicationService comparisonService,
                AgentEvaluationEvidenceExportApplicationService evidenceExportService) {
            return new AgentEvaluationControlPlaneController(
                    accessService, historyQueryService, comparisonService, evidenceExportService);
        }

        @Bean
        AgentEvaluationControlPlaneExceptionHandler exceptionHandler() {
            return new AgentEvaluationControlPlaneExceptionHandler();
        }

        @Bean
        JwtDecoder testJwtDecoder() {
            return token -> {
                throw new AssertionError("The MVC tests inject synthetic JWTs and must not decode a bearer token");
            };
        }

        @Bean
        PublicWebhookController publicWebhookController() {
            return new PublicWebhookController();
        }
    }

    @RestController
    static class PublicWebhookController {

        @GetMapping("/webhook/telegram")
        void webhook() {
        }
    }
}
