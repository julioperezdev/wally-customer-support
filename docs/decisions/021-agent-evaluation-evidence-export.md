# ADR-021 — Exportación versionada de evidencia de evaluación

- **Estado:** Accepted
- **Fecha:** 2026-09-08
- **Jira:** `WCS-64`
- **Ámbito:** bounded context `agent`, evidencia interna

## Contexto

WCS-60 ejecuta evaluaciones, WCS-61 persiste resultados sanitizados, WCS-62
consulta el histórico y WCS-63 compara runs baseline/candidate. El siguiente
consumidor será un job o backoffice protegido, pero todavía no existe un
contrato estable para transportar o adjuntar esa evidencia sin reintroducir
contenido conversacional.

## Decisión

Se define `AgentEvaluationEvidenceExport` como un envelope tipado y
versionado. Su versión inicial es `wcs.agent-evaluation-evidence.v1` y contiene
únicamente el resultado sanitizado de `AgentEvaluationComparison`:

- identidad de baseline y candidate, dataset y agente/versión;
- proveedor, modelo, timestamps y métricas agregadas;
- deltas de calidad y métricas operativas disponibles;
- escenarios identificados por `scenarioId`, estado y score;
- valores operativos ausentes representados como no disponibles.

La frontera acepta sólo dos runs mediante WCS-63 y limita el export a 1.000
escenarios. El resultado es apto para una serialización posterior, pero esta
tarea no crea endpoint, descarga, archivo ni integración externa.

## Límites de seguridad

El export no incluye prompts, respuestas, inputs, conversaciones, canales,
teléfonos, secretos, stack traces ni PII. No escribe ni modifica datos, no
decide promociones y no transforma metadata faltante en cero.

## Alternativas descartadas

- **Exportar entidades JPA directamente:** acopla el contrato externo al
  almacenamiento y puede filtrar campos no autorizados.
- **Generar JSON desde el dominio en esta tarea:** mezcla la frontera de
  aplicación con un formato de transporte que deberá decidir el backoffice o
  job autenticado.
- **Guardar el export en S3 automáticamente:** agrega retención, permisos y
  costos antes de aceptar su política operativa.

## Consecuencias

El futuro backoffice puede serializar una representación estable sin consultar
directamente PostgreSQL. Retención, autorización, almacenamiento, exportación
descargable y auditoría permanecen como decisiones posteriores.

