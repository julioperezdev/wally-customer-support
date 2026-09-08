# ADR-027 — Executor Bedrock opcional para evaluaciones con límites

Status: `Accepted`
Date: 2026-09-08
Related Jira: `WCS-82`, `WCS-83`, `WCS-84`
Related decisions: `ADR-026`

## Contexto

WCS ya dispone de un dataset sintético, un runner determinístico, persistencia
sanitizada y un trigger interno autenticado. El siguiente paso es comparar la
política de respuesta con un modelo real sin enviar conversaciones de clientes,
permitir SQL generado ni convertir el modelo en autoridad de negocio.

## Decisión

Se agrega un puerto `MeasuredLlmClient` que devuelve texto y metadata operativa
segura. Bedrock Converse implementa ese puerto y el executor de evaluaciones lo
usa únicamente cuando `wcs.agent-evaluation.executor=bedrock`. El executor
determinístico permanece como default y rollback.

La entrada del modelo está compuesta sólo por el caso de uso, canal y hechos
sintéticos ya validados por el dataset. El modelo no recibe el mensaje original
de un cliente, credenciales, prompts configurables desde el request, SQL,
tools ni acceso a PostgreSQL.

Antes de ejecutar se validan provider/model, cantidad máxima de escenarios y un
costo máximo estimado usando los límites y precios configurados. Las métricas
ausentes se preservan como `null`; no se inventan ceros. Las respuestas y
prompts no se persisten ni se escriben en logs.

## Configuración

```properties
wcs.agent-evaluation.executor=deterministic
wcs.agent-evaluation.max-scenarios=10
wcs.agent-evaluation.max-input-tokens-per-scenario=4000
wcs.agent-evaluation.max-output-tokens=512
wcs.agent-evaluation.max-estimated-cost-usd=0.0500
wcs.agent-evaluation.timeout=PT30S
```

Para una evaluación controlada, AppConfig puede cambiar `executor` a
`bedrock`, pero también deben coincidir `wcs.ai.provider=bedrock` y el
`wcs.ai.model` enviado por el trigger. La activación del trigger HTTP continúa
siendo una decisión separada y permanece deshabilitada por defecto.

## Consecuencias

* Se pueden comparar modelos reales con un dataset reproducible y sin mezclar
  datos de clientes.
* El histórico ya existente conserva tokens, latencia y costo estimado por
  escenario cuando Bedrock los devuelve.
* No existe todavía promoción automática, backoffice, canary, scheduler ni
  reconciliación con facturación de AWS.
* Si el límite de presupuesto no permite una suite, la ejecución se rechaza
  antes de invocar Bedrock.

## Rollback

Cambiar `wcs.agent-evaluation.executor` a `deterministic` y mantener el trigger
apagado. No requiere cambios de Terraform ni migraciones.
