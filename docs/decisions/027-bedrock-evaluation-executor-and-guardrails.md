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

La entrada depende del contrato evaluado y se construye sólo con datos
sintéticos validados por el dataset. `response-humanization` recibe el caso de
uso, canal y hechos estructurados; `conversation-router` recibe los mensajes e
historial sintéticos del escenario para reproducir una decisión de routing. El
modelo no recibe conversaciones de clientes, credenciales, prompts
configurables desde el request, SQL, tools ni acceso a PostgreSQL. El executor
acepta contratos cerrados: `response-humanization` con
`catalog-response-v1`, y `conversation-router` con
`conversation-routing-v1` o `conversation-routing-v2`. Los pares
agente/dataset se validan antes de invocar el modelo; otros agentes continúan
fallando cerrado.

`conversation-routing-v1` conserva su corpus original de 31 escenarios. La
versión `conversation-routing-v2` es un snapshot separado de 34 escenarios: los
31 mensajes e historiales originales conservan sus entradas y corrigen oráculos
de filtros que estaban incompletos; suma tres casos de cantidad de carrito y
mide esa extracción como dimensión propia. V1 no se reescribe ni se usa como
equivalente de V2: para comparar prompts deben ejecutarse baseline y candidata
contra la misma versión de dataset. La versión SQL del agente también debe
declarar ese `evaluationSuiteVersion` exacto.
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
misma cobertura de escenarios. Para el router se puntúan por separado intención,
acción y extracción de filtros; no se interpretan métricas de respuesta textual
como grounding o validez. El comparador muestra scorecard diferencial,
dimensiones especializadas sólo con cobertura idéntica y un resultado
descriptivo por escenario. No declara significancia estadística ni selecciona
un ganador. Ninguna ejecución activa, publica ni modifica una versión o
asignación, y no procesa tráfico real. El contrato de comparación está en
[`ADR-020`](020-agent-evaluation-comparison.md) y el envelope de evidencia
actual es `wcs.agent-evaluation-evidence.v2`.

## Configuración

```properties
wcs.agent-evaluation.executor=deterministic
wcs.agent-evaluation.max-scenarios=40
wcs.agent-evaluation.max-input-tokens-per-scenario=4000
wcs.agent-evaluation.max-output-tokens=1024
wcs.agent-evaluation.max-estimated-cost-usd=0.5000
wcs.agent-evaluation.timeout=PT30S
```

El límite de USD 0,50 aplica por run. El ejecutor estima el costo antes de la
primera inferencia y rechaza la suite si lo supera. El máximo de 40 escenarios
admite el corpus router completo de 31 casos y el límite de salida 1024 admite
el perfil router actual. AppConfig puede configurar límites aún más
restrictivos.

Para una evaluación controlada, AppConfig puede cambiar `executor` a
`bedrock`; el model ID y pricing se obtienen de la versión SQL, no de
`wcs.ai.model` ni del body del trigger. La activación del trigger HTTP continúa
siendo una decisión separada y permanece deshabilitada por defecto.

## Consecuencias

* Se pueden comparar versiones reales de agente con datasets reproducibles y
  sin mezclar datos de clientes ni cambiar tráfico activo. Para el router se
  persisten intención, acción, filtros, modelo, tokens, latencia y costo
  estimado; no se guarda el mensaje ni la salida textual.
* El histórico ya existente conserva tokens, latencia y costo estimado por
  escenario cuando Bedrock los devuelve.
* El backoffice consulta y compara runs; no existe promoción automática,
  canary, scheduler ni reconciliación con facturación de AWS.
* Si el límite de presupuesto o cualquier contrato del snapshot no permite una
  suite, la ejecución se rechaza antes de invocar Bedrock.

## Rollback

Cambiar `wcs.agent-evaluation.executor` a `deterministic` y mantener el trigger
apagado. No requiere cambios de Terraform ni migraciones.
