# ADR-040 — Scorecard de calidad y observabilidad de evaluaciones

## Estado

Accepted for local-first implementation — 2026-09-18

## Contexto

WCS ya persiste runs de evaluación y registra pass rate, score, tokens, costo y
latencia. Esas métricas no distinguían entre una respuesta con formato válido,
una respuesta que preservó los hechos esperados y una respuesta que introdujo
contenido prohibido. Además, no era visible qué dimensiones todavía no tenían
un oracle de evaluación.

## Decisión

Cada suite produce un `AgentEvaluationQualityScorecard` sanitizado con:

- `responseValidityRate`: existe respuesta y coincide el outcome esperado;
- `responseGroundingRate`: conserva los fragmentos de hechos requeridos;
- `safetyRate`: no contiene fragmentos explícitamente prohibidos;
- `utilityRate`: score promedio del evaluador actual;
- conteo de razones de fallo;
- lista explícita de dimensiones no evaluadas.

El servicio emite `AGENT_EVALUATION_SCORECARD` además de
`AGENT_EVALUATION_COMPLETED`. Ambos eventos contienen sólo metadata agregada:
no se registran prompts, respuestas, escenarios completos, secretos ni PII.

Las dimensiones `intent_accuracy`, `entity_extraction`, `tool_success` y
`rag_grounding` quedan marcadas como no disponibles hasta que exista un dataset
y oracle para ellas. La ausencia de una métrica no se reemplaza con cero.

## Reglas de comparación

1. Runs con distinto `datasetVersion` no se comparan.
2. Grafana agrupa por dataset, agente, versión, proveedor, modelo y hora.
3. El scorecard informa evidencia; no publica ni promociona automáticamente un
   agente.
4. El costo y la latencia siguen siendo métricas operativas separadas y se
   conservan como `null` cuando el proveedor no entrega usage.

## Consecuencias

El equipo puede distinguir una regresión de grounding de una regresión de
seguridad y observar la cobertura real del evaluador. La próxima evolución
puede agregar oracles de routing, extracción de entidades, tools y RAG sin
romper el contrato actual.

No se agrega una migración de base de datos en esta fase: el run ya conserva el
resultado sanitizado y el scorecard se deriva de él y se expone como evento
agregado. Esto mantiene el cambio local-first y evita duplicar métricas en SQL.
