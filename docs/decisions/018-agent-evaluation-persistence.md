# ADR-018 — Persistencia de resultados de evaluación

- **Estado:** Accepted
- **Fecha:** 2026-09-08
- **Jira:** `WCS-61`
- **Ámbito:** bounded context `agent`, runs offline de evaluación

## Contexto

WCS-60 definió una frontera de aplicación para ejecutar datasets sintéticos y
producir un `AgentEvaluationRun` sanitizado. El resultado era sólo memoria del
proceso: no podía compararse una ejecución con otra ni ser consumido luego por
un job o backoffice. La persistencia no debe convertir el histórico de
evaluación en un repositorio de conversaciones o prompts.

## Decisión

Persistir los runs completados en dos tablas del schema `wcs`:

- `agent_evaluation_runs` contiene la identidad versionada del dataset y del
  agente, métricas agregadas, timestamps, duración y razones de fallo
  acotadas.
- `agent_evaluation_scenario_results` contiene el resultado por escenario y,
  cuando existe, metadata operativa de ejecución: modelo, proveedor, latencia,
  tokens, costo estimado y versión de pricing.

La aplicación usa un puerto `AgentEvaluationRunRepository` y un adapter JPA.
El adapter rechaza sobrescrituras de un `runId` existente: los resultados son
inmutables una vez guardados. Sólo se intenta guardar después de completar la
suite; un fallo de ejecución no crea un run parcial. Flyway crea las tablas,
claves foráneas, índices y restricciones de métricas.

La información persistida no incluye prompts, respuestas, inputs de escenarios,
texto de catálogo, identificadores de canal, teléfono, chat ID ni PII. El
contrato actual se mantiene interno: no se agrega endpoint HTTP ni permisos de
ejecución remota como parte de esta decisión.

## Alternativas descartadas

- **Guardar el JSON completo de cada evaluación:** facilita una exportación
  rápida, pero aumenta el riesgo de retener contenido sensible y acopla el
  storage a una versión del contrato.
- **Una tabla única con JSONB:** simplifica el primer insert, pero dificulta
  constraints, consultas por escenario y evolución controlada.
- **Permitir updates para corregir runs:** rompe la trazabilidad histórica;
  una nueva ejecución debe crear otro `runId`.
- **Persistir en un almacén separado:** no aporta valor mientras WCS ya usa el
  schema PostgreSQL y el volumen es bajo.

## Consecuencias

El histórico puede alimentar comparaciones y futuros reportes sin reejecutar la
suite. Deben definirse antes de exponerlo a usuarios: autorización, política de
retención, paginación/exportación y estrategia de índices para el volumen real.
La migración es aditiva y el rollback operativo consiste en dejar de invocar el
puerto; no se deben borrar tablas con datos sin una decisión explícita de
retención.
