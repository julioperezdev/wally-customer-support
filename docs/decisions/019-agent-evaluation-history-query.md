# ADR-019 — Consulta interna del histórico de evaluaciones

- **Estado:** Accepted
- **Fecha:** 2026-09-08
- **Jira:** `WCS-62`
- **Ámbito:** bounded context `agent`, lectura offline de resultados

## Contexto

WCS-61 creó un histórico relacional de runs completados y escenarios
sanitizados. El siguiente consumidor será un job o un backoffice protegido,
pero antes hace falta un contrato de lectura que no dependa de HTTP, UI ni de
una implementación de Spring Data. La consulta debe ser comparable y segura:
no puede devolver una colección ilimitada ni contenido conversacional.

## Decisión

La aplicación expone internamente `AgentEvaluationHistoryQueryService` con dos
operaciones:

- búsqueda de resúmenes mediante filtros opcionales de dataset, agente,
  versión, proveedor, modelo y rango de finalización;
- detalle sanitizado por `runId`, reutilizando el agregado persistido.

La página tiene `pageNumber` y `pageSize`, con tamaño máximo de 100. El adapter
traduce el request a `Pageable` y ordena siempre por `completedAt` descendente y
`id` ascendente como desempate. Los filtros se normalizan y se enlazan como
parámetros de la consulta JPQL; no existe SQL generado por el usuario o por un
LLM.

La consulta sigue siendo una frontera interna. No agrega controller, permisos,
endpoint, exportación, borrado ni retención automática. El resultado de lista
es `AgentEvaluationRunSummary`; el detalle sólo contiene los campos ya
permitidos por WCS-61.

## Alternativas descartadas

- **Devolver todos los runs:** permite uso rápido, pero no ofrece límite de
  costo ni protección ante crecimiento del histórico.
- **Exponer el repositorio JPA al backoffice:** acopla consumidores a la
  persistencia y permite saltar las reglas de aplicación.
- **Permitir orden arbitrario:** dificulta reproducibilidad y paginación estable.
- **Crear un endpoint antes de autorización:** expone métricas internas sin
  definir identidad, roles ni auditoría.

## Consecuencias

Los futuros consumidores tienen un contrato pequeño y testeable para filtros,
paginación y detalle. Antes de habilitar acceso remoto deben definirse
autorización, auditoría, retención y exportación. La consulta no requiere una
nueva migración ni cambios de infraestructura.
