package com.wally.customersupport.agent.infrastructure.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BackofficePreviewSecurityConfigurationTest.SecurityTestConfiguration.class)
@TestPropertySource(properties = {
        "wcs.backoffice.preview.enabled=true",
        "wcs.backoffice.preview.token=preview-token",
        "wcs.agent-evaluation.control-plane.security.enabled=false"
})
@WebAppConfiguration
class BackofficePreviewSecurityConfigurationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void requiresThePreviewToken() throws Exception {
        mockMvc.perform(get("/internal/backoffice/catalog"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsReadOnlyCatalogWithThePreviewToken() throws Exception {
        mockMvc.perform(get("/internal/backoffice/catalog")
                        .header("Authorization", "Bearer preview-token"))
                .andExpect(status().isOk());
    }

    @Test
    void allowsReadOnlyPreflightAndSimulationWithThePreviewToken() throws Exception {
        mockMvc.perform(post("/internal/agent-registry/activations/preflight")
                        .header("Authorization", "Bearer preview-token"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/internal/backoffice/agent-map/simulations")
                        .header("Authorization", "Bearer preview-token"))
                .andExpect(status().isOk());
    }

    @Test
    void deniesWritesEvenWithThePreviewToken() throws Exception {
        mockMvc.perform(post("/internal/backoffice/catalog/variants/SKU/stock")
                        .header("Authorization", "Bearer preview-token"))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import(PreviewController.class)
    static class SecurityTestConfiguration {

        @Bean
        BackofficePreviewAuthenticationFilter previewAuthenticationFilter() {
            return new BackofficePreviewAuthenticationFilter("preview-token");
        }

        @Bean
        SecurityFilterChain previewFilterChain(
                HttpSecurity http,
                BackofficePreviewAuthenticationFilter previewFilter) throws Exception {
            return new AgentEvaluationControlPlaneSecurityConfiguration()
                    .backofficePreviewSecurityFilterChain(http, previewFilter);
        }

        @Bean
        SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
            return new AgentEvaluationControlPlaneSecurityConfiguration()
                    .securityDisabledFilterChain(http);
        }
    }

    @RestController
    static class PreviewController {

        @GetMapping("/internal/backoffice/catalog")
        String catalog() {
            return "ok";
        }

        @PostMapping("/internal/backoffice/catalog/variants/SKU/stock")
        String stock() {
            return "changed";
        }

        @PostMapping("/internal/agent-registry/activations/preflight")
        String preflight() {
            return "ready";
        }

        @PostMapping("/internal/backoffice/agent-map/simulations")
        String simulation() {
            return "unchanged";
        }
    }
}
