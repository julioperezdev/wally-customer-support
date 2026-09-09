package com.wally.customersupport.featureflag.application;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static FeatureFlagRuntimeService service(String payload) {
        MutableSource source = new MutableSource();
        source.next.set(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return service(source);
    }

    private static FeatureFlagRuntimeService service(FeatureFlagConfigurationSource source) {
        FeatureFlagProperties properties = new FeatureFlagProperties(
                true,
                true,
                30_000,
                300_000,
                new FeatureFlagProperties.AppConfig("wally-customer-support", "prod", "feature-flags"),
                new FeatureFlagProperties.Publisher(false, "all-at-once"));
        return new FeatureFlagRuntimeService(
                source,
                (content, description) -> new FeatureFlagConfigurationPublisher.Publication("published", "1"),
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
