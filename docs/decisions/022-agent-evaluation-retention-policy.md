# ADR-022 — Política explícita de retención de evidencia de evaluación

- **Estado:** Accepted for implementation boundary; policy activation pending
- **Fecha:** 2026-09-08
- **Jira:** `WCS-65`
- **Ámbito:** bounded context `agent`, lifecycle de evidencia

## Contexto

WCS persiste evidencia agregada y sanitizada de evaluaciones para comparar
versiones y medir calidad. La existencia de una fecha de vencimiento no debe
convertirse automáticamente en borrado: la purga requiere autorización,
retención aprobada por ambiente, auditoría y un mecanismo operativo reversible.

## Decisión

Se agrega una política explícita con una duración positiva y una decisión
determinística por run. El vencimiento se calcula como:

`completedAt + completedRunRetention`

Antes del instante de vencimiento el run queda `ACTIVE`; en el instante de
vencimiento o después queda `EXPIRED`. La decisión sólo contiene el `runId`,
las fechas y el estado. No lee ni expone contenido de escenarios.

La recomendación inicial para revisión de Producto/Legal es retener 90 días
los runs completados. Es una propuesta de evaluación y no un default productivo:
ningún bean de configuración, job o workflow activa purgas con esta tarea.

## Límites

- No se borran, archivan ni modifican runs.
- No se agrega scheduler, endpoint, configuración de AppConfig, S3, Lambda,
  SQS ni infraestructura AWS.
- No se mezclan los resultados de evaluación con la retención de conversaciones
  o memoria de clientes.
- Los consumidores deben entregar el instante de evaluación; no se usa el reloj
  del sistema de forma implícita.

## Consecuencias

WCS-66 permite revisar una página histórica y obtener decisiones y contadores
sin mutar runs. Un futuro job autenticado podrá consultar esa revisión antes de
proponer una acción de retención. La activación deberá definir por separado autorización,
auditoría, retención por ambiente, período de gracia, paginación, métricas,
reintentos y rollback.
