# ADR-024 — API read-only del control plane de evaluaciones

- Estado: Accepted
- Fecha: 2026-09-08
- Relacionado: `WCS-73`, `WCS-74`, `WCS-75`

## Contexto

WCS ya puede guardar runs sanitizados, consultar su histórico, comparar dos
ejecuciones y exportar evidencia versionada. Esas capacidades todavía no deben
quedar disponibles para un backoffice o job mediante una API sin una frontera
de autorización explícita. También necesitamos reducir la fricción operativa:
los contratos de acceso, lectura y pruebas deben evolucionar como un slice
coherente, sin elegir todavía un proveedor técnico.

## Decisión

Se agrega `/internal/agent-evaluations` como fachada HTTP read-only protegida
por un contrato propio:

| Operación | Endpoint | Servicio de aplicación |
| --- | --- | --- |
| Histórico | `GET /runs` | `AgentEvaluationHistoryQueryService` |
| Detalle | `GET /runs/{runId}` | `AgentEvaluationHistoryQueryService` |
| Comparación | `GET /comparisons` | `AgentEvaluationComparisonApplicationService` |
| Evidencia | `GET /evidence` | `AgentEvaluationEvidenceExportApplicationService` |

La fachada recibe un identificador de actor estable y pseudónimo en
`X-WCS-Actor-Id`. El ambiente no lo decide el caller: el servicio de acceso
usa `wcs.agent-evaluation.control-plane.allowed-environment`, cuyo default es
`prod`. La única capacidad de esta API es `agent-evaluation.read`.

El `AgentEvaluationControlPlaneAuthorizer` es un port de aplicación que podrá
adaptarse después a OIDC, IAM, un gateway o el mecanismo aprobado. La
composición Spring instala un authorizer que devuelve `false` si no existe uno
explícito. Falta de actor, ambiente/capacidad no permitidos, rechazo o error
del provider producen una decisión `DENIED`; nunca se propaga la excepción al
caller.

Los servicios de histórico, comparación y export se invocan sólo después de
autorizar. Los errores se exponen como códigos estables (`ACCESS_DENIED`,
`INVALID_REQUEST`, `RUN_NOT_FOUND`, `INCOMPATIBLE_DATASET` e
`INTERNAL_ERROR`) sin mensajes internos, prompts, respuestas, tokens ni PII.
Los eventos de acceso registran operación, capacidad, resultado, razón y
duración; no registran el actor crudo.

## Alternativas descartadas

- Spring Security/OIDC/IAM en este slice: requieren definir identidad,
  trust-boundary, roles y operación de infraestructura; se incorporarán en una
  issue específica cuando el provider sea aceptado.
- Exponer directamente los servicios de aplicación: acoplaría HTTP con el
  control plane y permitiría saltar la autorización.
- Permitir ejecución desde esta API: aumenta el impacto y mezcla lectura con
  triggers idempotentes que tienen su propia frontera.
- Aceptar SQL, prompts o contenido conversacional: contradice el modelo de
  evaluación sanitizada y la separación de responsabilidades.

## Consecuencias

La API queda lista para un backoffice o un job, pero cerrada en producción por
default deny. La futura implementación del authorizer no obliga a modificar
los servicios de dominio o aplicación. Las pruebas MockMvc garantizan el
orden de seguridad y evitan que una solicitud denegada alcance PostgreSQL.

## Rollout y rollback

No se crean recursos AWS ni migraciones nuevas. El rollout requiere desplegar
la aplicación con el endpoint aún cerrado; para habilitarlo se debe agregar un
authorizer explícito, revisar la configuración del ambiente y ejecutar las
pruebas de contrato. El rollback es retirar la composición/controlador o
volver al commit anterior; no hay cambios de datos que revertir.
