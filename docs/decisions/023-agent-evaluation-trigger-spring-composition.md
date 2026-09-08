# ADR-023 — Composición Spring del trigger interno de evaluaciones

- **Estado:** Accepted for implementation boundary
- **Fecha:** 2026-09-08
- **Jira:** `WCS-71`
- **Ámbito:** bounded context `agent`, composición de infraestructura

## Contexto

WCS ya define una frontera de autorización y una ejecución interna idempotente
para futuras evaluaciones. Todavía no existe un entry point remoto ni un
proveedor de autenticación técnica aprobado. Dejar estos servicios sin
componer en Spring haría fácil agregar un consumidor accidentalmente sin
mantener el cierre seguro.

## Decisión

`AgentEvaluationTriggerConfiguration` registra por constructor:

- `AgentEvaluationTriggerAuthorizationService`, usando el ambiente permitido
  desde `wcs.agent-evaluation.authorization.allowed-environment` y `prod` como
  fallback seguro;
- `AgentEvaluationTriggerExecutionService`, conectado al servicio de
  evaluación y al guard PostgreSQL existente;
- un `AgentEvaluationTriggerAuthorizer` que deniega por defecto cuando no
  existe un adapter concreto.

El authorizer default se registra con `@ConditionalOnMissingBean`, por lo que
un proveedor explícito futuro puede reemplazarlo sin modificar el servicio de
aplicación. La autorización sigue validando actor, ambiente, capacidad y key
antes de reclamar idempotencia o ejecutar una evaluación.

## Límites

- No agrega endpoint, scheduler, consumidor remoto ni integración OIDC/IAM.
- No habilita la ejecución de evaluaciones en ningún ambiente por sí sola.
- No agrega permisos AWS, AppConfig, Secrets Manager ni cambios de Terraform.
- No persiste credenciales, tokens, prompts, respuestas ni PII.

## Consecuencias

El contexto Spring queda listo para inyectar la frontera interna, pero el
comportamiento productivo continúa siendo fail-closed hasta seleccionar y
configurar un proveedor de autenticación técnica. La prueba de contexto usa
PostgreSQL 16 de Testcontainers y confirma la denegación por defecto sin
invocar el guard ni el executor.
