# ADR-020 — Comparación sanitizada de runs de evaluación

- **Estado:** Accepted
- **Fecha:** 2026-09-08
- **Jira:** `WCS-63`
- **Ámbito:** bounded context `agent`, comparación offline

## Contexto

WCS-61 persiste runs completados y WCS-62 permite consultarlos de forma
filtrable y paginada. Para evaluar una nueva versión hace falta comparar un run
base con uno candidato por calidad y operación. La comparación no debe leer
prompts o respuestas ni decidir por sí misma una promoción.

## Decisión

`AgentEvaluationComparisonApplicationService` recibe dos `runId`: el primero
es el baseline y el segundo el candidate. Sólo compara runs existentes del
mismo dataset; un dataset diferente produce un error de aplicación
sanitizado. El resultado contiene:

- identidad y métricas agregadas sanitizadas de ambos runs;
- deltas de escenarios aprobados/fallidos, pass rate, score y duración;
- diferencias de tokens, latencia del proveedor y costo estimado cuando ambos
  runs tienen metadata completa;
- comparación por `scenarioId`, estado y score, sin contenido de evaluación.

Las métricas operativas se representan como no disponibles cuando falta un
valor en cualquiera de los runs. No se reemplaza la ausencia por cero. El
contrato es interno y no depende de HTTP, JPA, Bedrock ni frameworks de
agentes.

## Alternativas descartadas

- **Elegir automáticamente un ganador:** mezcla medición con una decisión de
  publicación y puede promover una regresión.
- **Comparar texto generado:** introduce contenido sensible y una evaluación
  difícil de reproducir; la calidad textual requiere un evaluador específico y
  una política posterior.
- **Tratar metadata faltante como cero:** genera deltas falsos y oculta la
  diferencia entre no medido y consumo real cero.
- **Permitir datasets diferentes:** hace que los deltas de escenarios y calidad
  carezcan de significado.

## Consecuencias

El futuro backoffice puede mostrar evidencia comparable antes de activar una
versión. La promoción, los umbrales, el peso de cada métrica, autorización,
retención y auditoría siguen siendo decisiones posteriores. No se agrega una
nueva migración ni se modifica infraestructura.
