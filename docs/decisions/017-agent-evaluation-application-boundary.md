# ADR-017 — Frontera de aplicación para evaluaciones trazables

- Estado: Accepted for WCS-60
- Fecha: 2026-09-07
- Alcance: ejecución interna de datasets sintéticos versionados

## Contexto

WCS ya define escenarios (`WCS-56`), un runner determinístico (`WCS-57`) y
metadata operativa por escenario (`WCS-58`). Esos contratos todavía sólo pueden
usarse desde tests o código que conozca directamente la lista de escenarios.
Para comparar futuras versiones de agentes hace falta una entrada estable que
resuelva un dataset por versión y produzca un identificador de ejecución.

## Decisión

Se agrega una frontera de aplicación compuesta por:

- `AgentEvaluationDatasetCatalog`, que resuelve providers de datasets
  sintéticos por `datasetVersion` y rechaza versiones no registradas;
- `AgentEvaluationApplicationService`, que recibe la identidad declarada del
  agente/modelo y un `AgentEvaluationExecutor` inyectable;
- `AgentEvaluationRun`, que devuelve `runId`, timestamps, duración, identidad
  operativa y el `AgentEvaluationSuiteResult` sanitizado;
- eventos `AGENT_EVALUATION_COMPLETED` y `AGENT_EVALUATION_FAILED` con métricas
  agregadas y sin contenido de evaluación.

El catálogo comienza con `catalog-response-v1`. El resultado es in-memory y la
frontera no se expone aún por HTTP. Un futuro job o endpoint deberá incorporar
autorización explícita antes de ejecutarse en un ambiente compartido.

## Límites de seguridad

- no se aceptan prompts ni tools como configuración de esta entrada;
- no se invoca Bedrock ni se genera SQL;
- no se conservan respuestas, prompts, conversaciones ni PII;
- los eventos registran sólo dimensiones operativas y `errorType`, nunca el
  mensaje o stack trace de una excepción;
- un dataset desconocido no se convierte en un fallback silencioso.

## Consecuencias

La evaluación puede ejecutarse con el mismo contrato desde tests, un job futuro
o un backoffice autenticado sin acoplar el dominio a Spring MVC, Bedrock o una
base de datos de resultados. La próxima evolución debe decidir persistencia,
exportación y autorización conjuntamente con la política de retención y los
gates de promoción.

## Fuera de alcance

Persistencia, endpoint público, backoffice, comparación de modelos reales,
canary, promoción, AgentCore Memory, MCP y cambios de infraestructura.
