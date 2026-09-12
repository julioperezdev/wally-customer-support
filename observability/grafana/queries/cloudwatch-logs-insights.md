# Consultas de CloudWatch Logs Insights — WCS

Estas consultas se ejecutan desde Grafana Explore usando el datasource
`WCS CloudWatch` y los log groups de App Runner bajo
`/aws/apprunner/wally-customer-support-prod-backend`.

WCS emite eventos operativos como una línea JSON con el campo común
`eventFamily=WCS_EVENT`, `schemaVersion`, `eventId`, `eventType`, `service` y
`occurredAt`. Los requests HTTP agregan `requestId` y lo propagan a los
eventos síncronos del flujo. Spring Boot puede agregar un prefijo textual a la
línea; por eso las consultas usan `parse` explícito en vez de depender del
descubrimiento automático de campos JSON.

## Requests HTTP por ruta, resultado y latencia

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"HTTP_REQUEST_COMPLETED\"/
| parse @message /\"httpMethod\":\"(?<parsedHttpMethod>[^\"]+)\"/
| parse @message /\"route\":\"(?<parsedRoute>[^\"]+)\"/
| parse @message /\"httpStatus\":(?<parsedHttpStatus>[0-9]+)/
| parse @message /\"outcome\":\"(?<parsedOutcome>[^\"]+)\"/
| parse @message /\"durationMs\":(?<parsedDurationMs>[0-9]+)/
| stats count() as requests,
        avg(parsedDurationMs) as averageDurationMs,
        pct(parsedDurationMs, 50) as p50DurationMs,
        pct(parsedDurationMs, 95) as p95DurationMs,
        sum(if(parsedOutcome = \"SERVER_ERROR\" or parsedOutcome = \"ERROR\", 1, 0)) as failures
  by parsedHttpMethod, parsedRoute, parsedHttpStatus, parsedOutcome, bin(1h)
| sort @timestamp asc
```

El response header `X-Request-Id` permite buscar todos los eventos de una
ejecución concreta. Para métricas agregadas no se debe usar `requestId` como
dimensión: es un valor de alta cardinalidad y sólo debe utilizarse para
diagnóstico puntual.

Los eventos no incluyen texto de usuario, prompts, respuestas completas,
secretos, números de teléfono ni tokens de autenticación. `inputTokens`,
`outputTokens` y `totalTokens` son contadores de consumo del proveedor de IA,
no credenciales.

## Consultas agregadas por tipo, resultado y latencia

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"CONVERSATION_QUERY_COMPLETED\"/
| parse @message /\"queryType\":\"(?<parsedQueryType>[^\"]+)\"/
| parse @message /\"outcome\":\"(?<parsedOutcome>[^\"]+)\"/
| parse @message /\"durationMs\":(?<parsedDurationMs>[0-9]+)/
| stats count() as queries,
        avg(parsedDurationMs) as averageDurationMs,
        pct(parsedDurationMs, 95) as p95DurationMs
  by parsedQueryType, parsedOutcome, bin(1h)
| sort @timestamp asc
```

## Detalle de cada consulta y trazabilidad segura del actor

Esta variante no usa `stats` ni `bin(1h)`: conserva una fila por evento y el
`@timestamp` individual que CloudWatch asigna al log. La tabla puede mostrar
hasta 100 consultas recientes dentro de la ventana seleccionada.

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"CONVERSATION_QUERY_COMPLETED\"/
| parse @message /\"channel\":\"(?<parsedChannel>[^\"]+)\"/
| parse @message /\"actorKey\":\"(?<parsedActorKey>[^\"]+)\"/
| parse @message /\"queryType\":\"(?<parsedQueryType>[^\"]+)\"/
| parse @message /\"outcome\":\"(?<parsedOutcome>[^\"]+)\"/
| parse @message /\"durationMs\":(?<parsedDurationMs>[0-9]+)/
| parse @message /\"workflowVersion\":\"(?<parsedWorkflowVersion>[^\"]+)\"/
| parse @message /\"correlationId\":\"(?<parsedCorrelationId>[^\"]+)\"/
| parse @message /\"fallbackReason\":\"(?<parsedFallbackReason>[^\"]+)\"/
| sort @timestamp desc
| limit 100
| display @timestamp, parsedChannel, parsedActorKey, parsedQueryType,
          parsedOutcome, parsedDurationMs, parsedWorkflowVersion,
          parsedCorrelationId, parsedFallbackReason
```

`actorKey` es un HMAC-SHA-256 estable por canal y cliente, generado con una
clave exclusiva del servidor en Secrets Manager. Es útil para agrupar eventos
del mismo actor, pero no reemplaza una identidad de usuario en el producto ni
permite recuperar el teléfono o chat ID. Si la clave no está configurada, el
evento no incluye `actorKey`; la consulta continúa funcionando. `correlationId`
identifica la conversación y se reserva para diagnóstico puntual, no para
dimensiones agregadas de alta cardinalidad.

## Uso de IA por operación, modelo y costo estimado

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"AI_USAGE_RECORDED\"/
| parse @message /\"stage\":\"(?<parsedStage>[^\"]+)\"/
| parse @message /\"operation\":\"(?<parsedOperation>[^\"]+)\"/
| parse @message /\"provider\":\"(?<parsedProvider>[^\"]+)\"/
| parse @message /\"model\":\"(?<parsedModel>[^\"]+)\"/
| parse @message /\"channel\":\"(?<parsedChannel>[^\"]+)\"/
| parse @message /\"useCase\":\"(?<parsedUseCase>[^\"]+)\"/
| parse @message /\"pricingVersion\":\"(?<parsedPricingVersion>[^\"]+)\"/
| parse @message /\"success\":(?<parsedSuccess>true|false)/
| parse @message /\"inputTokens\":(?<parsedInputTokens>[0-9]+)/
| parse @message /\"outputTokens\":(?<parsedOutputTokens>[0-9]+)/
| parse @message /\"totalTokens\":(?<parsedTotalTokens>[0-9]+)/
| parse @message /\"estimatedCostUsd\":(?<parsedEstimatedCostUsd>[0-9.]+)/
| parse @message /\"durationMs\":(?<parsedDurationMs>[0-9]+)/
| stats count() as calls,
        sum(parsedInputTokens) as inputTokens,
        sum(parsedOutputTokens) as outputTokens,
        sum(parsedTotalTokens) as totalTokens,
        sum(parsedEstimatedCostUsd) as estimatedCostUsd,
        avg(parsedDurationMs) as averageDurationMs,
        pct(parsedDurationMs, 95) as p95DurationMs,
        sum(if(parsedSuccess = "false", 1, 0)) as failures
  by parsedStage, parsedOperation, parsedProvider, parsedModel, parsedPricingVersion, bin(1h)
| sort @timestamp asc
```

`estimatedCostUsd` se calcula con los precios por millón de tokens de la
configuración efectiva y se identifica con `pricingVersion`. Es un estimado
operativo: no reemplaza la facturación de AWS ni incluye eventuales cargos
adicionales.

## Últimas llamadas de IA

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"AI_USAGE_RECORDED\"/
| parse @message /\"operation\":\"(?<parsedOperation>[^\"]+)\"/
| parse @message /\"provider\":\"(?<parsedProvider>[^\"]+)\"/
| parse @message /\"model\":\"(?<parsedModel>[^\"]+)\"/
| parse @message /\"totalTokens\":(?<parsedTotalTokens>[0-9]+)/
| parse @message /\"estimatedCostUsd\":(?<parsedEstimatedCostUsd>[0-9.]+)/
| parse @message /\"durationMs\":(?<parsedDurationMs>[0-9]+)/
| parse @message /\"success\":(?<parsedSuccess>true|false)/
| display @timestamp, parsedOperation, parsedProvider, parsedModel, parsedSuccess,
          parsedTotalTokens, parsedEstimatedCostUsd, parsedDurationMs
| sort @timestamp desc
| limit 100
```

El adapter mock no realiza una llamada de IA y por eso no emite
`AI_USAGE_RECORDED`. Para ver consumo real, AppConfig debe seleccionar un
proveedor real, por ejemplo `wcs.ai.provider=bedrock`.

## Flujo de mensajes por canal y resultado

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"INBOUND_MESSAGE_PROCESSED\"/
| parse @message /\"channel\":\"(?<parsedChannel>[^\"]+)\"/
| parse @message /\"result\":\"(?<parsedResult>[^\"]+)\"/
| parse @message /\"durationMs\":(?<parsedDurationMs>[0-9]+)/
| stats count() as messages, avg(parsedDurationMs) as averageDurationMs
  by parsedChannel, parsedResult, bin(1h)
| sort @timestamp asc
```

## Intenciones detectadas

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"INTENT_CLASSIFIED\"/
| parse @message /\"intent\":\"(?<parsedIntent>[^\"]+)\"/
| parse @message /\"confidence\":(?<parsedConfidence>[0-9.]+)/
| stats count() as classifications, avg(parsedConfidence) as averageConfidence by parsedIntent, bin(1h)
| sort @timestamp asc
```

## Eventos de salida

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"OUTBOUND_MESSAGE_DISPATCHED\"/
| parse @message /\"channel\":\"(?<parsedChannel>[^\"]+)\"/
| parse @message /\"result\":\"(?<parsedResult>[^\"]+)\"/
| parse @message /\"durationMs\":(?<parsedDurationMs>[0-9]+)/
| stats count() as deliveries, avg(parsedDurationMs) as averageDurationMs
  by parsedChannel, parsedResult, bin(1h)
| sort @timestamp asc
```

## Webhooks rechazados

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"WEBHOOK_REJECTED\"/
| parse @message /\"channel\":\"(?<parsedChannel>[^\"]+)\"/
| parse @message /\"reason\":\"(?<parsedReason>[^\"]+)\"/
| stats count() as rejections by parsedChannel, parsedReason, bin(1h)
| sort @timestamp asc
```

## Retrieval de conocimiento

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"RAG_RETRIEVAL_RECORDED\"/
| parse @message /\"provider\":\"(?<parsedProvider>[^\"]+)\"/
| parse @message /\"success\":(?<parsedSuccess>true|false)/
| parse @message /\"resultCount\":(?<parsedResultCount>[0-9]+)/
| parse @message /\"durationMs\":(?<parsedDurationMs>[0-9]+)/
| stats count() as retrievals,
        avg(parsedResultCount) as averageResults,
        avg(parsedDurationMs) as averageDurationMs,
        sum(if(parsedSuccess = "false", 1, 0)) as failures
  by parsedProvider, bin(1h)
| sort @timestamp asc
```

La operación de retrieval no devuelve uso de tokens de generación; el costo
de generación se observa en `AI_USAGE_RECORDED` cuando el LLM redacta la
respuesta.

## Comparación shadow por resultado y calidad

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"AGENT_TRAFFIC_COMPARISON_RECORDED\"/
| parse @message /\"agentId\":\"(?<parsedAgentId>[^\"]+)\"/
| parse @message /\"agentVersion\":(?<parsedAgentVersion>[0-9]+)/
| parse @message /\"model\":\"(?<parsedModel>[^\"]+)\"/
| parse @message /\"channel\":\"(?<parsedChannel>[^\"]+)\"/
| parse @message /\"useCase\":\"(?<parsedUseCase>[^\"]+)\"/
| parse @message /\"outcome\":\"(?<parsedOutcome>[^\"]+)\"/
| parse @message /\"comparisonOutcome\":\"(?<parsedComparisonOutcome>[^\"]+)\"/
| stats count() as executions,
        sum(if(parsedComparisonOutcome = "MATCH", 1, 0)) as matches,
        sum(if(parsedComparisonOutcome = "MISMATCH", 1, 0)) as mismatches,
        sum(if(parsedComparisonOutcome = "UNKNOWN", 1, 0)) as unknowns,
        sum(if(parsedOutcome = "COMPLETED", 1, 0)) as completed,
        sum(if(parsedOutcome != "COMPLETED", 1, 0)) as failures,
        (sum(if(parsedComparisonOutcome = "MATCH", 1, 0)) * 100.0 / count()) as matchRatePercent,
        (sum(if(parsedComparisonOutcome = "MISMATCH", 1, 0)) * 100.0 / count()) as mismatchRatePercent,
        (sum(if(parsedComparisonOutcome = "UNKNOWN", 1, 0)) * 100.0 / count()) as unknownRatePercent
  by parsedAgentId, parsedAgentVersion, parsedModel, parsedChannel, parsedUseCase, bin(1h)
| sort @timestamp desc
```

Esta consulta sólo usa metadata sanitizada. `UNKNOWN` no debe interpretarse
como éxito; el scorecard lo trata como evidencia insuficiente cuando supera el
umbral aprobado.

## Comparación shadow por latencia, tokens y costo

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"AGENT_TRAFFIC_COMPARISON_RECORDED\"/
| parse @message /\"agentId\":\"(?<parsedAgentId>[^\"]+)\"/
| parse @message /\"agentVersion\":(?<parsedAgentVersion>[0-9]+)/
| parse @message /\"model\":\"(?<parsedModel>[^\"]+)\"/
| parse @message /\"channel\":\"(?<parsedChannel>[^\"]+)\"/
| parse @message /\"useCase\":\"(?<parsedUseCase>[^\"]+)\"/
| parse @message /\"latencyMs\":(?<parsedLatencyMs>[0-9]+)/
| parse @message /\"inputTokens\":(?<parsedInputTokens>[0-9]+)/
| parse @message /\"outputTokens\":(?<parsedOutputTokens>[0-9]+)/
| parse @message /\"totalTokens\":(?<parsedTotalTokens>[0-9]+)/
| parse @message /\"estimatedCostUsd\":(?<parsedEstimatedCostUsd>[0-9.]+)/
| stats count() as executions,
        avg(parsedLatencyMs) as averageLatencyMs,
        pct(parsedLatencyMs, 50) as p50LatencyMs,
        pct(parsedLatencyMs, 95) as p95LatencyMs,
        sum(parsedInputTokens) as inputTokens,
        sum(parsedOutputTokens) as outputTokens,
        sum(parsedTotalTokens) as totalTokens,
        sum(parsedEstimatedCostUsd) as estimatedCostUsd
  by parsedAgentId, parsedAgentVersion, parsedModel, parsedChannel, parsedUseCase, bin(1h)
| sort @timestamp desc
```

Los campos operativos pueden ser nulos cuando el proveedor no entrega
metadata. Esa ausencia impide aprobar el scorecard; no se reemplaza con cero.

## Errores de aplicación

```text
fields @timestamp, @message
| filter @message like /ERROR|Exception/
| sort @timestamp desc
| limit 100
```

Este último panel es sólo diagnóstico. Si una excepción futura pudiera
contener PII o secretos, debe sanitizarse en el backend antes de consultarla.
