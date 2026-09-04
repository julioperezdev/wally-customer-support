# Consultas de CloudWatch Logs Insights — WCS

Estas consultas se ejecutan desde Grafana Explore usando el datasource
`WCS CloudWatch` y los log groups de App Runner bajo
`/aws/apprunner/wally-customer-support-prod-backend`.

WCS emite eventos operativos como una línea JSON con el campo común
`eventFamily=WCS_EVENT`, `schemaVersion`, `eventType`, `service` y
`occurredAt`. Spring Boot puede agregar un prefijo textual a la línea; por eso
las consultas usan `parse` explícito en vez de depender del descubrimiento
automático de campos JSON.

Los eventos no incluyen texto de usuario, prompts, respuestas completas,
secretos, números de teléfono ni tokens de autenticación. `inputTokens`,
`outputTokens` y `totalTokens` son contadores de consumo del proveedor de IA,
no credenciales.

## Consultas completas por tipo, resultado y latencia

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

## Uso de IA por operación, modelo y costo estimado

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"AI_USAGE_RECORDED\"/
| parse @message /\"stage\":\"(?<parsedStage>[^\"]+)\"/
| parse @message /\"operation\":\"(?<parsedOperation>[^\"]+)\"/
| parse @message /\"provider\":\"(?<parsedProvider>[^\"]+)\"/
| parse @message /\"model\":\"(?<parsedModel>[^\"]+)\"/
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

## Errores de aplicación

```text
fields @timestamp, @message
| filter @message like /ERROR|Exception/
| sort @timestamp desc
| limit 100
```

Este último panel es sólo diagnóstico. Si una excepción futura pudiera
contener PII o secretos, debe sanitizarse en el backend antes de consultarla.
