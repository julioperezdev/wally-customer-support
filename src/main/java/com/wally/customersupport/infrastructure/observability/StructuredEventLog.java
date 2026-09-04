package com.wally.customersupport.infrastructure.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import tools.jackson.databind.ObjectMapper;

/** Emits one-line JSON events that can be queried without logging message content. */
public final class StructuredEventLog {

    public static final String EVENT_FAMILY = "WCS_EVENT";
    private static final int SCHEMA_VERSION = 1;
    private static final String SERVICE = "wally-customer-support";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private StructuredEventLog() {
    }

    public static void info(Logger logger, String eventType, Map<String, ?> fields) {
        emit(logger, eventType, fields, false);
    }

    public static void warn(Logger logger, String eventType, Map<String, ?> fields) {
        emit(logger, eventType, fields, true);
    }

    private static void emit(Logger logger, String eventType, Map<String, ?> fields, boolean warning) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventFamily", EVENT_FAMILY);
        event.put("schemaVersion", SCHEMA_VERSION);
        event.put("eventType", eventType);
        event.put("service", SERVICE);
        event.put("occurredAt", Instant.now().toString());
        if (fields != null) {
            fields.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    event.put(key, value);
                }
            });
        }

        try {
            String payload = OBJECT_MAPPER.writeValueAsString(event);
            if (warning) {
                logger.warn(payload);
            } else {
                logger.info(payload);
            }
        } catch (RuntimeException exception) {
            logger.warn("WCS_EVENT eventType=STRUCTURED_EVENT_SERIALIZATION_FAILED originalEventType={}", eventType);
        }
    }
}
