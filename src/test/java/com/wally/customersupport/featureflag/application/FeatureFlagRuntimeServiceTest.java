package com.wally.customersupport.featureflag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.wally.customersupport.featureflag.application.port.FeatureFlagConfigurationPublisher;
import com.wally.customersupport.featureflag.application.port.FeatureFlagConfigurationSource;
import com.wally.customersupport.featureflag.application.service.FeatureFlagRuntimeService;
import com.wally.customersupport.featureflag.infrastructure.config.FeatureFlagProperties;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class FeatureFlagRuntimeServiceTest {

    @Test
    void replacesTheSnapshotAtomicallyWhenAValidVersionArrives() throws Exception {
        String payload = """
                {"schemaVersion":"1","version":"v2","flags":[
                  {"key":"wcs.agent.catalog-specialist.enabled","enabled":false,"killSwitch":true,
                   "environments":["prod"],"channels":["telegram"],"useCases":["catalog-search"],
                   "agentIds":["catalog-specialist"],"agentVersions":[2]}]}
                """;
        FeatureFlagRuntimeService service = service(payload);

        service.refresh();

        assertThat(service.view().effectiveVersion()).isEqualTo("v2");
        assertThat(service.isAgentExecutionAllowed(new FeatureFlagContext(
                "prod", "telegram", "catalog-search", "catalog-specialist", 2))).isFalse();
        assertThat(service.view().audit()).anyMatch(entry -> "ACCEPTED".equals(entry.result()));
    }

    @Test
    void rejectsSecretsAndKeepsTheLastValidSnapshot() throws Exception {
        MutableSource source = new MutableSource();
        source.next.set("""
                {"schemaVersion":"1","version":"valid","flags":[]}
                """.getBytes());
        FeatureFlagRuntimeService service = service(source);
        service.refresh();
        source.next.set("""
                {"schemaVersion":"1","version":"bad","token":"must-not-be-here","flags":[]}
                """.getBytes());

        service.refresh();

        assertThat(service.view().effectiveVersion()).isEqualTo("valid");
        assertThat(service.view().audit()).anyMatch(entry -> "REJECTED".equals(entry.result()));
    }

    @Test
    void publishesAValidatedVersionAndMakesItEffectiveImmediately() {
        FeatureFlagConfigurationPublisher publisher = mock(FeatureFlagConfigurationPublisher.class);
        when(publisher.publish("{\"schemaVersion\":\"1\",\"version\":\"v2\",\"flags\":[]}",
                "WCS feature flags published by operator"))
                .thenReturn(new FeatureFlagConfigurationPublisher.Publication("v2", "deployment-2"));
        FeatureFlagRuntimeService service = service(new MutableSource(), publisher, true);

        service.publish(new FeatureFlagDocument("1", "v2", List.of()), "operator");

        assertThat(service.view().effectiveVersion()).isEqualTo("v2");
        assertThat(service.view().audit()).anyMatch(entry ->
                "publish".equals(entry.operation()) && "operator".equals(entry.actor()));
        verify(publisher).publish("{\"schemaVersion\":\"1\",\"version\":\"v2\",\"flags\":[]}",
                "WCS feature flags published by operator");
    }

    @Test
    void rollsBackToThePreviousEffectiveVersionAndAuditsTheOperation() {
        FeatureFlagConfigurationPublisher publisher = mock(FeatureFlagConfigurationPublisher.class);
        when(publisher.publish("{\"schemaVersion\":\"1\",\"version\":\"v2\",\"flags\":[]}",
                "WCS feature flags published by operator"))
                .thenReturn(new FeatureFlagConfigurationPublisher.Publication("v2", "deployment-2"));
        when(publisher.publish("{\"schemaVersion\":\"1\",\"version\":\"v1\",\"flags\":[]}",
                "WCS feature flags rollback by operator"))
                .thenReturn(new FeatureFlagConfigurationPublisher.Publication("v3", "deployment-3"));
        MutableSource source = new MutableSource();
        source.next.set("{\"schemaVersion\":\"1\",\"version\":\"v1\",\"flags\":[]}".getBytes());
        FeatureFlagRuntimeService service = service(source, publisher, true);
        service.refresh();

        service.publish(new FeatureFlagDocument("1", "v2", List.of()), "operator");
        service.rollback("operator");

        assertThat(service.view().effectiveVersion()).isEqualTo("v1");
        assertThat(service.view().audit()).anyMatch(entry ->
                "rollback".equals(entry.operation()) && "operator".equals(entry.actor()));
        verify(publisher).publish("{\"schemaVersion\":\"1\",\"version\":\"v1\",\"flags\":[]}",
                "WCS feature flags rollback by operator");
    }

    @Test
    void rollbackRestoresThePreviousAgentExecutionDecision() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        FeatureFlagDefinition enabled = new FeatureFlagDefinition(
                "wcs.agent.catalog-specialist.enabled", true, false,
                List.of("prod"), List.of("telegram"), List.of("catalog-search"),
                List.of("catalog-specialist"), List.of(2));
        FeatureFlagDefinition disabled = new FeatureFlagDefinition(
                "wcs.agent.catalog-specialist.enabled", false, true,
                List.of("prod"), List.of("telegram"), List.of("catalog-search"),
                List.of("catalog-specialist"), List.of(2));
        FeatureFlagDocument v1 = new FeatureFlagDocument("1", "v1", List.of(enabled));
        FeatureFlagDocument v2 = new FeatureFlagDocument("1", "v2", List.of(disabled));
        String v1Json = mapper.writeValueAsString(v1);
        String v2Json = mapper.writeValueAsString(v2);
        FeatureFlagConfigurationPublisher publisher = mock(FeatureFlagConfigurationPublisher.class);
        when(publisher.publish(v2Json, "WCS feature flags published by operator"))
                .thenReturn(new FeatureFlagConfigurationPublisher.Publication("v2", "deployment-2"));
        when(publisher.publish(v1Json, "WCS feature flags rollback by operator"))
                .thenReturn(new FeatureFlagConfigurationPublisher.Publication("v3", "deployment-3"));
        MutableSource source = new MutableSource();
        source.next.set(v1Json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        FeatureFlagRuntimeService service = service(source, publisher, true);
        FeatureFlagContext context = new FeatureFlagContext(
                "prod", "telegram", "catalog-search", "catalog-specialist", 2);

        service.refresh();
        assertThat(service.isAgentExecutionAllowed(context)).isTrue();

        service.publish(v2, "operator");
        assertThat(service.isAgentExecutionAllowed(context)).isFalse();

        service.rollback("operator");

        assertThat(service.view().effectiveVersion()).isEqualTo("v1");
        assertThat(service.isAgentExecutionAllowed(context)).isTrue();
        assertThat(service.view().audit()).anyMatch(entry ->
                "rollback".equals(entry.operation()) && "ACCEPTED".equals(entry.result()));
    }

    private static FeatureFlagRuntimeService service(String payload) {
        MutableSource source = new MutableSource();
        source.next.set(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return service(source);
    }

    private static FeatureFlagRuntimeService service(FeatureFlagConfigurationSource source) {
        return service(source, (content, description) -> new FeatureFlagConfigurationPublisher.Publication("published", "1"), false);
    }

    private static FeatureFlagRuntimeService service(
            FeatureFlagConfigurationSource source,
            FeatureFlagConfigurationPublisher publisher,
            boolean publisherEnabled) {
        FeatureFlagProperties properties = new FeatureFlagProperties(
                true,
                true,
                30_000,
                300_000,
                new FeatureFlagProperties.AppConfig("wally-customer-support", "prod", "feature-flags"),
                new FeatureFlagProperties.Publisher(publisherEnabled, "all-at-once"));
        return new FeatureFlagRuntimeService(
                source,
                publisher,
                new FeatureFlagValidator(new ObjectMapper()),
                properties,
                new ObjectMapper());
    }

    private static final class MutableSource implements FeatureFlagConfigurationSource {
        private final AtomicReference<byte[]> next = new AtomicReference<>();

        @Override
        public Optional<FeatureFlagPayload> poll() {
            byte[] payload = next.getAndSet(null);
            return payload == null ? Optional.empty() : Optional.of(new FeatureFlagPayload(payload));
        }
    }
}
