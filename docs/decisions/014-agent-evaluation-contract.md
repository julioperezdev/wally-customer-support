# ADR-014 — Contrato de evaluación de agentes y políticas

- Estado: Accepted for WCS-56
- Fecha: 2026-09-07
- Alcance: dataset sintético y evaluación de políticas de respuesta

## Decisión

WCS define `AgentEvaluationScenario` como un escenario sintético versionado.
Identifica el caso de uso, canal, entrada estructurada, resultado esperado y
fragmentos requeridos/prohibidos. No contiene conversaciones reales, PII,
secretos ni prompts.

`ResponsePolicyEvaluator` compara un `ResponseHumanizationResult` con el
escenario y devuelve `AgentEvaluationResult` con `passed`, `score` y razones
sanitizadas. La evaluación no guarda el texto de respuesta y no llama a
Bedrock. Los motivos son códigos estables (`OUTCOME_MISMATCH`,
`REQUIRED_TEXT_MISSING`, `FORBIDDEN_TEXT_PRESENT`, etc.) para agregarlos en
métricas.

El dataset inicial `catalog-response-v1` cubre coincidencia, ausencia de
coincidencias, aclaración, alternativas y fallback seguro. Se usa para probar
la política determinística actual y como contrato de entrada para un futuro
runner de agentes/modelos.

## Límites

- El score no representa por sí solo calidad humana ni autoriza promoción.
- No se evalúan modelos reales ni se cambia el runtime productivo.
- No se ejecuta SQL ni se consultan datos de producción.
- Los escenarios deben usar datos sintéticos y resultados estructurados.

## Evolución

Un runner posterior podrá ejecutar la misma suite contra varias versiones de
agente, registrar latencia/tokens/costo como metadata separada y comparar
regresiones. La promoción requerirá criterios adicionales de grounding,
políticas, costo y revisión humana.
