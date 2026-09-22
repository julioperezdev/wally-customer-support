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
determinístico permanece como default y rollback. Para Bedrock, el executor
resuelve `agentId` y `agentVersion` contra una versión inmutable de
`wcs.agent_versions`; no consulta `agent_activations` ni requiere que la versión
esté activa. Se permite evaluar una candidata, una versión activa o una retirada,
pero nunca una DRAFT editable.

La entrada del modelo está compuesta sólo por el caso de uso, canal y hechos
sintéticos ya validados por el dataset. El modelo no recibe el mensaje original
de un cliente, credenciales, prompts configurables desde el request, SQL,
tools ni acceso a PostgreSQL. La primera implementación sólo acepta
`response-humanization` con el dataset de respuesta de catálogo; los demás
agentes requieren un executor/dataset con contrato específico y fallan cerrado.
System prompt, template, model ID, temperatura, `topP`, razonamiento, límites,
timeout y precio salen del snapshot SQL exacto. El request no puede cambiar
esos parámetros; `provider` y `modelId` son opcionales por compatibilidad y se
ignoran en el executor Bedrock. Puede elegirse la versión por número SQL o por
Semantic Version.

Antes de ejecutar se valida existencia y estado de la versión, hash del prompt,
schemas JSON, placeholders permitidos, pricing y límites de escenario/costo.
El límite global de salida rechaza una versión que lo exceda; no cambia
silenciosamente su configuración. El costo máximo se estima con los precios de
la versión seleccionada. Las métricas ausentes se preservan como `null`; no se
inventan ceros. Se persisten el dataset, agent ID/versión, provider/model reales
y métricas sanitizadas; las respuestas y prompts no se persisten ni se escriben
en logs. El evento de uso Bedrock identifica SemVer y hash del prompt.

Comparar una baseline y una candidata significa ejecutar dos runs separados
del mismo agente lógico, contra el mismo dataset versionado y exactamente la
misma cobertura de escenarios. El comparador muestra scorecard diferencial,
dimensiones especializadas sólo con cobertura idéntica y un resultado
descriptivo por escenario. No declara significancia estadística ni selecciona
un ganador. Ninguna ejecución activa, publica ni modifica una versión o
asignación, y no procesa tráfico real. El contrato de comparación está en
[`ADR-020`](020-agent-evaluation-comparison.md) y el envelope de evidencia
actual es `wcs.agent-evaluation-evidence.v2`.

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
`bedrock`; el model ID y pricing se obtienen de la versión SQL, no de
`wcs.ai.model` ni del body del trigger. La activación del trigger HTTP continúa
siendo una decisión separada y permanece deshabilitada por defecto.

## Consecuencias

* Se pueden comparar versiones reales de agente con un dataset reproducible y
  sin mezclar datos de clientes ni cambiar tráfico activo.
* El histórico ya existente conserva tokens, latencia y costo estimado por
  escenario cuando Bedrock los devuelve.
* No existe todavía promoción automática, backoffice, canary, scheduler ni
  reconciliación con facturación de AWS.
* Si el límite de presupuesto o cualquier contrato del snapshot no permite una
  suite, la ejecución se rechaza antes de invocar Bedrock.

## Rollback

Cambiar `wcs.agent-evaluation.executor` a `deterministic` y mantener el trigger
apagado. No requiere cambios de Terraform ni migraciones.
