# Scorecard de piloto — CloudWatch Logs Insights

Estas consultas usan únicamente eventos `WCS_EVENT` y devuelven agregados. No
incluyen `@message` en el resultado final para no exportar texto de logs,
mensajes, prompts o datos de proveedor. Ejecutarlas en el mismo período UTC
del reporte de [`docs/pilot-report-template.md`](../../docs/pilot-report-template.md).

## Resolución, fallback, handoff y latencia

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"CONVERSATION_QUERY_COMPLETED\"/
| parse @message /\"queryType\":\"(?<queryType>[^\"]+)\"/
| parse @message /\"outcome\":\"(?<outcome>[^\"]+)\"/
| parse @message /\"durationMs\":(?<durationMs>[0-9]+)/
| stats count() as queries,
        sum(if(outcome = "REPLIED", 1, 0)) as replied,
        sum(if(outcome = "FALLBACK", 1, 0)) as fallback,
        sum(if(outcome = "LOW_CONFIDENCE", 1, 0)) as lowConfidence,
        sum(if(outcome = "HANDOFF", 1, 0)) as handoff,
        avg(durationMs) as averageDurationMs,
        pct(durationMs, 50) as p50DurationMs,
        pct(durationMs, 95) as p95DurationMs
  by queryType, bin(1h)
| sort @timestamp asc
```

## Mensajes procesados, errores y duplicados

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"INBOUND_MESSAGE_PROCESSED\"/
   or @message like /\"eventType\":\"INBOUND_MESSAGE_FAILED\"/
   or @message like /\"eventType\":\"INBOUND_MESSAGE_RETRY_SCHEDULED\"/
   or @message like /\"eventType\":\"INBOUND_MESSAGE_ENQUEUED\"/
| parse @message /\"eventType\":\"(?<eventType>[^\"]+)\"/
| parse @message /\"result\":\"(?<result>[^\"]+)\"/
| parse @message /\"channel\":\"(?<channel>[^\"]+)\"/
| stats count() as events,
        sum(if(eventType = "INBOUND_MESSAGE_PROCESSED" and result = "COMPLETED", 1, 0)) as processed,
        sum(if(eventType = "INBOUND_MESSAGE_FAILED", 1, 0)) as failed,
        sum(if(eventType = "INBOUND_MESSAGE_RETRY_SCHEDULED", 1, 0)) as retrying,
        sum(if(eventType = "INBOUND_MESSAGE_ENQUEUED" and result = "DUPLICATE", 1, 0)) as duplicates
  by channel, bin(1h)
| sort @timestamp asc
```

## Entrega outbound

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"OUTBOUND_MESSAGE_DISPATCHED\"/
| parse @message /\"channel\":\"(?<channel>[^\"]+)\"/
| parse @message /\"result\":\"(?<result>[^\"]+)\"/
| parse @message /\"durationMs\":(?<durationMs>[0-9]+)/
| stats count() as deliveries,
        sum(if(result = "SENT", 1, 0)) as sent,
        sum(if(result = "FAILED", 1, 0)) as failed,
        avg(durationMs) as averageDurationMs,
        pct(durationMs, 95) as p95DurationMs
  by channel, bin(1h)
| sort @timestamp asc
```

## IA: llamadas, tokens, costo y latencia

```text
fields @timestamp, @message
| filter @message like /\"eventType\":\"AI_USAGE_RECORDED\"/
| parse @message /\"provider\":\"(?<provider>[^\"]+)\"/
| parse @message /\"model\":\"(?<model>[^\"]+)\"/
| parse @message /\"success\":(?<success>true|false)/
| parse @message /\"inputTokens\":(?<inputTokens>[0-9]+)/
| parse @message /\"outputTokens\":(?<outputTokens>[0-9]+)/
| parse @message /\"totalTokens\":(?<totalTokens>[0-9]+)/
| parse @message /\"estimatedCostUsd\":(?<estimatedCostUsd>[0-9.]+)/
| parse @message /\"durationMs\":(?<durationMs>[0-9]+)/
| stats count() as calls,
        sum(inputTokens) as inputTokens,
        sum(outputTokens) as outputTokens,
        sum(totalTokens) as totalTokens,
        sum(estimatedCostUsd) as estimatedCostUsd,
        avg(durationMs) as averageDurationMs,
        pct(durationMs, 95) as p95DurationMs,
        sum(if(success = "false", 1, 0)) as failures
  by provider, model, bin(1h)
| sort @timestamp asc
```

## Seguridad y diagnóstico puntual

La consulta de seguridad se hace con el panel de eventos operativos y revisión
manual de una muestra. No se exportan líneas completas. Si aparece un secreto,
token, prompt, mensaje, teléfono o chat ID, se pausa el piloto y se aplica el
rollback del runbook.

Para diagnosticar una ejecución puntual se puede filtrar por el `requestId`
devuelto en `X-Request-Id`, o por un `correlationId` interno ya sanitizado. No
se debe usar un identificador externo del canal como dimensión o evidencia.
