package com.wally.customersupport.featureflag.application.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import com.wally.customersupport.featureflag.application.FeatureFlagAuditEntry;
import com.wally.customersupport.featureflag.application.FeatureFlagContext;
import com.wally.customersupport.featureflag.application.FeatureFlagDefinition;
import com.wally.customersupport.featureflag.application.FeatureFlagDocument;
import com.wally.customersupport.featureflag.application.FeatureFlagSnapshotView;
import com.wally.customersupport.featureflag.application.FeatureFlagValidationException;
import com.wally.customersupport.featureflag.application.FeatureFlagValidator;
import com.wally.customersupport.featureflag.application.port.FeatureFlagConfigurationPublisher;
import com.wally.customersupport.featureflag.application.port.FeatureFlagConfigurationSource;
import com.wally.customersupport.featureflag.infrastructure.config.FeatureFlagProperties;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Owns the immutable effective flag snapshot. Readers are lock-free; refreshes
 * validate completely before the atomic reference is replaced.
 */
@Service
@Slf4j
public class FeatureFlagRuntimeService {

    private static final int MAX_AUDIT_ENTRIES = 100;
    private final FeatureFlagConfigurationSource source;
    private final FeatureFlagConfigurationPublisher publisher;
    private final FeatureFlagValidator validator;
    private final FeatureFlagProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicReference<EffectiveSnapshot> current;
    private final Deque<EffectiveSnapshot> history = new ArrayDeque<>();
    private final List<FeatureFlagAuditEntry> audit = new ArrayList<>();
    private final ReentrantLock refreshLock = new ReentrantLock();

    public FeatureFlagRuntimeService(
            FeatureFlagConfigurationSource source,
            FeatureFlagConfigurationPublisher publisher,
            FeatureFlagValidator validator,
            FeatureFlagProperties properties,
            ObjectMapper objectMapper) {
        this.source = source;
        this.publisher = publisher;
        this.validator = validator;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.current = new AtomicReference<>(EffectiveSnapshot.empty(properties.appconfig().environment()));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadAtStartup() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${wcs.feature-flags.poll-interval-ms:30000}")
    public void pollAppConfig() {
        refresh();
    }

    public void refresh() {
        if (!properties.enabled()) {
            return;
        }
        if (!refreshLock.tryLock()) {
            return;
        }
        try {
            source.poll().ifPresent(payload -> accept(payload.content(), "system", "refresh"));
        } catch (RuntimeException exception) {
            markStale(exception);
        } finally {
            refreshLock.unlock();
        }
    }

    public boolean isEnabled(String key, FeatureFlagContext context) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(context, "context");
        return current.get().find(key, context).map(flag -> flag.enabled() && !flag.killSwitch()).orElse(false);
    }

    /** Missing agent flags preserve the registry decision; configured flags fail closed. */
    public boolean isAgentExecutionAllowed(FeatureFlagContext context) {
        if (current.get().stale() && properties.failClosed()) {
            return false;
        }
        String key = "wcs.agent." + context.agentId() + ".enabled";
        return current.get().find(key, context).map(flag -> flag.enabled() && !flag.killSwitch()).orElse(true);
    }

    public FeatureFlagSnapshotView view() {
        EffectiveSnapshot snapshot = current.get();
        synchronized (audit) {
            return snapshot.view(List.copyOf(audit));
        }
    }

    public FeatureFlagSnapshotView publish(FeatureFlagDocument document, String actor) {
        if (!properties.publisher().enabled()) {
            throw new FeatureFlagValidationException("feature flag publication is disabled");
        }
        String normalizedActor = actor == null || actor.isBlank() ? "unknown" : actor.strip();
        validator.validate(document, properties.appconfig().environment());
        try {
            String content = objectMapper.writeValueAsString(document);
            FeatureFlagConfigurationPublisher.Publication publication = publisher.publish(
                    content,
                    "WCS feature flags published by " + normalizedActor);
            accept(content.getBytes(java.nio.charset.StandardCharsets.UTF_8), normalizedActor, "publish");
            recordAudit("publish", normalizedActor, publication.version(), "ACCEPTED", publication.deploymentNumber());
            return view();
        } catch (FeatureFlagValidationException exception) {
            recordAudit("publish", normalizedActor, document.version(), "REJECTED", exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            recordAudit("publish", normalizedActor, document.version(), "FAILED", exception.getClass().getSimpleName());
            throw new IllegalStateException("feature flag publication failed", exception);
        }
    }

    public FeatureFlagSnapshotView rollback(String actor) {
        if (!properties.publisher().enabled()) {
            throw new FeatureFlagValidationException("feature flag publication is disabled");
        }
        EffectiveSnapshot previous;
        synchronized (history) {
            previous = history.peekLast();
        }
        if (previous == null) {
            throw new FeatureFlagValidationException("no previous approved feature flag version is available");
        }
        String normalizedActor = actor == null || actor.isBlank() ? "unknown" : actor.strip();
        try {
            FeatureFlagConfigurationPublisher.Publication publication = publisher.publish(
                    previous.rawContent(),
                    "WCS feature flags rollback by " + normalizedActor);
            replace(previous, normalizedActor, "rollback");
            recordAudit("rollback", normalizedActor, publication.version(), "ACCEPTED", publication.deploymentNumber());
            return view();
        } catch (RuntimeException exception) {
            recordAudit("rollback", normalizedActor, previous.document().version(), "FAILED", exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private void accept(byte[] content, String actor, String operation) {
        FeatureFlagDocument document = validator.validateJson(content, properties.appconfig().environment());
        EffectiveSnapshot next = EffectiveSnapshot.of(
                properties.appconfig().environment(), document, content, Instant.now());
        EffectiveSnapshot old = current.getAndSet(next);
        if (!old.document().version().equals(next.document().version())) {
            synchronized (history) {
                if (!old.document().version().equals("none")) {
                    history.removeIf(item -> item.document().version().equals(old.document().version()));
                    history.addLast(old);
                    while (history.size() > 10) history.removeFirst();
                }
            }
        }
        recordAudit(operation, actor, next.document().version(), "ACCEPTED", "effective");
        StructuredEventLog.info(log, "FEATURE_FLAGS_REFRESH_ACCEPTED", Map.of(
                "environment", properties.appconfig().environment(),
                "effectiveVersion", next.document().version(),
                "flagCount", next.document().flags().size(),
                "operation", operation));
    }

    private void replace(EffectiveSnapshot snapshot, String actor, String operation) {
        EffectiveSnapshot old = current.getAndSet(snapshot.withLoadedAt(Instant.now()));
        synchronized (history) {
            if (!old.document().version().equals("none")) history.addLast(old);
        }
        recordAudit(operation, actor, snapshot.document().version(), "ACCEPTED", "effective");
    }

    private void markStale(RuntimeException exception) {
        EffectiveSnapshot snapshot = current.get();
        long age = snapshot.loadedAt().equals(Instant.EPOCH)
                ? Long.MAX_VALUE
                : java.time.Duration.between(snapshot.loadedAt(), Instant.now()).toMillis();
        if (age >= properties.effectiveStaleAfterMs()) {
            current.compareAndSet(snapshot, snapshot.withStale(true));
        }
        StructuredEventLog.warn(log, "FEATURE_FLAGS_REFRESH_REJECTED", Map.of(
                "environment", properties.appconfig().environment(),
                "effectiveVersion", snapshot.document().version(),
                "stale", current.get().stale(),
                "reason", exception.getClass().getSimpleName()));
        recordAudit("refresh", "system", snapshot.document().version(), "REJECTED", exception.getClass().getSimpleName());
    }

    private void recordAudit(String operation, String actor, String version, String result, String reason) {
        synchronized (audit) {
            audit.add(new FeatureFlagAuditEntry(operation, actor, version, Instant.now(), result, reason));
            while (audit.size() > MAX_AUDIT_ENTRIES) audit.remove(0);
        }
    }

    private record EffectiveSnapshot(
            String environment,
            FeatureFlagDocument document,
            String rawContent,
            Instant loadedAt,
            boolean stale) {

        static EffectiveSnapshot empty(String environment) {
            return new EffectiveSnapshot(environment, new FeatureFlagDocument("1", "none", List.of()), "{\"schemaVersion\":\"1\",\"version\":\"none\",\"flags\":[]}", Instant.EPOCH, false);
        }

        static EffectiveSnapshot of(String environment, FeatureFlagDocument document, byte[] raw, Instant loadedAt) {
            return new EffectiveSnapshot(environment, document, new String(raw, java.nio.charset.StandardCharsets.UTF_8), loadedAt, false);
        }

        EffectiveSnapshot withStale(boolean value) {
            return new EffectiveSnapshot(environment, document, rawContent, loadedAt, value);
        }

        EffectiveSnapshot withLoadedAt(Instant value) {
            return new EffectiveSnapshot(environment, document, rawContent, value, false);
        }

        java.util.Optional<FeatureFlagDefinition> find(String key, FeatureFlagContext context) {
            return document.flags().stream()
                    .filter(flag -> key.equals(flag.key()) && flag.matches(context))
                    .findFirst();
        }

        FeatureFlagSnapshotView view(List<FeatureFlagAuditEntry> audit) {
            return new FeatureFlagSnapshotView(
                    environment,
                    document.version(),
                    loadedAt,
                    loadedAt.equals(Instant.EPOCH) ? null : loadedAt,
                    stale,
                    document.flags(),
                    audit);
        }
    }
}
