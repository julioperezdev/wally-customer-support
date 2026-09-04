# Consultas de CloudWatch Logs Insights — WCS

Estas consultas se ejecutan desde Grafana Explore usando el datasource
`WCS CloudWatch` y los log groups de App Runner bajo
`/aws/apprunner/wally-customer-support-prod-backend`.

Los eventos `WCS_EVENT` contienen únicamente dimensiones operativas. No se
registran el texto de la consulta, prompts, tokens, números de teléfono ni
chat IDs.

## Flujo de mensajes por canal y resultado

```text
fields @timestamp, @message
| filter @message like /WCS_EVENT/
| parse @message /eventType=(?<eventType>[^ ]+)/
| parse @message /channel=(?<channel>[^ ]+)/
| parse @message /result=(?<result>[^ ]+)/
| filter eventType = "INBOUND_MESSAGE_PROCESSED"
| stats count() as messages by channel, result, bin(1h)
| sort @timestamp asc
```

## Intenciones detectadas

```text
fields @timestamp, @message
| filter @message like /eventType=INTENT_CLASSIFIED/
| parse @message /intent=(?<intent>[^ ]+)/
| parse @message /confidence=(?<confidence>[^ ]+)/
| stats count() as classifications, avg(confidence) as averageConfidence by intent, bin(1h)
| sort @timestamp asc
```

## Eventos de salida

```text
fields @timestamp, @message
| filter @message like /eventType=OUTBOUND_MESSAGE_DISPATCHED/
| parse @message /channel=(?<channel>[^ ]+)/
| parse @message /result=(?<result>[^ ]+)/
| stats count() as deliveries by channel, result, bin(1h)
| sort @timestamp asc
```

## Webhooks rechazados

```text
fields @timestamp, @message
| filter @message like /eventType=WEBHOOK_REJECTED/
| parse @message /channel=(?<channel>[^ ]+)/
| parse @message /reason=(?<reason>[^ ]+)/
| stats count() as rejections by channel, reason, bin(1h)
| sort @timestamp asc
```

## Errores de aplicación

```text
fields @timestamp, @message
| filter @message like /ERROR|Exception/
| sort @timestamp desc
| limit 100
```

Este último panel es sólo diagnóstico. Si una excepción futura pudiera
contener PII o secretos, debe sanitizarse en el backend antes de consultarla.
