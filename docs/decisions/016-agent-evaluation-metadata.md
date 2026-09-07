# ADR-016 — Metadata operativa de evaluación

- Estado: Accepted for WCS-58
- Fecha: 2026-09-07
- Alcance: metadata in-memory de una ejecución de evaluación

## Decisión

WCS separa la calidad de la respuesta de la telemetría de ejecución mediante
`AgentEvaluationExecutionMetadata`. El contrato puede identificar agente,
versión, proveedor y modelo, y registrar duración, latencia del proveedor,
tokens de entrada/salida/total, costo estimado y versión de pricing.

Los contadores y el costo son opcionales: si el proveedor no devuelve usage o
no existe una tabla de precios válida, el valor queda ausente. No se reemplaza
la ausencia por cero porque eso produciría métricas falsas.

`AgentEvaluationExecution` transporta temporalmente la respuesta y metadata;
`AgentEvaluationResult` conserva sólo el resultado sanitizado y la metadata,
nunca el texto. El runner continúa siendo in-memory y no persiste ejecuciones.

## Límites

- Duración, latencia, tokens y costo no pueden ser negativos.
- El costo se expresa en USD estimados y debe conservar su versión de pricing.
- La metadata no contiene prompts, respuestas, secretos, teléfonos ni PII.
- La metadata no autoriza promoción, activación ni cambio de configuración.

## Evolución

Los adapters Bedrock podrán construir este contrato usando los datos de usage y
latencia que devuelve el proveedor. Una fase posterior podrá emitir estos
campos en `AI_USAGE_RECORDED` o exportarlos a un sistema de métricas, siempre
manteniendo la separación entre telemetría y hechos de negocio.
