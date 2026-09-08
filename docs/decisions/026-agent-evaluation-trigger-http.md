# ADR-026 — Trigger HTTP autenticado para evaluaciones

- Estado: `Accepted for implementation`
- Fecha: 2026-09-08
- Jira: `WCS-79`, `WCS-80`, `WCS-81`
- Predecesor: [`ADR-025`](025-agent-evaluation-control-plane-jwt-security.md)

## Contexto

WCS ya tiene un servicio provider-neutral para autorizar y ejecutar una suite
de evaluación, un guard PostgreSQL idempotente y una API read-only protegida
por JWT. Falta una frontera controlada para que un futuro backoffice o job
confiable inicie una evaluación sin introducir SQL, prompts, credenciales o
datos de conversación en la petición.

## Decisión

Se agrega `POST /internal/agent-evaluations/runs`, deshabilitado por defecto
mediante `wcs.agent-evaluation.trigger.enabled=false`.

El request sólo acepta:

```json
{
  "datasetVersion": "catalog-response-v1",
  "agentId": "catalog-specialist",
  "agentVersion": "v1",
  "provider": "mock",
  "modelId": "deterministic-v1"
}
```

El actor se obtiene del `sub` del JWT y la key se recibe en
`Idempotency-Key`. La identidad no puede ser enviada por header alternativo ni
por el body. La capa HTTP delega en `AgentEvaluationTriggerExecutionService`;
la autorización ocurre antes del guard y el guard antes de la evaluación.

La seguridad aplica mínimo privilegio:

| Operación | Ruta | Authority |
| --- | --- | --- |
| Leer histórico, detalle, comparación y evidencia | `GET /internal/agent-evaluations/**` | `SCOPE_agent-evaluation.read` |
| Iniciar una evaluación | `POST /internal/agent-evaluations/runs` | `SCOPE_agent-evaluation.execute` |

El executor habilitado inicialmente es el evaluador determinístico de la
política de respuesta actual. Bedrock real, comparación automática y
promoción quedan fuera de este slice.

## Contratos de respuesta

- `200 COMPLETED`: incluye `runId`.
- `409 ALREADY_PROCESSED`: la key ya fue reclamada; no ejecuta otra vez.
- `403 DENIED`: la frontera de aplicación rechazó la autorización.
- `400 INVALID_REQUEST`: body o header inválido.
- `401`: JWT ausente o inválido, resuelto por Spring Security.
- `500 FAILED` o `INTERNAL_ERROR`: fallo interno sanitizado.

Las respuestas no incluyen authorization reasons internos, prompts, respuestas
evaluadas, claims completos, excepciones ni secretos.

## Alternativas descartadas

- Aceptar un actor enviado por el cliente: permite suplantación.
- Aceptar SQL, prompts o tools en el request: amplía de forma insegura la
  superficie de ejecución.
- Exponer el endpoint con el scope de lectura: viola mínimo privilegio.
- Ejecutar asíncronamente con SQS en este slice: se reserva para una etapa en
  que las evaluaciones reales requieran desacoplar latencia y reintentos.

## Rollout y rollback

El endpoint permanece apagado hasta configurar un IdP, issuer, audience,
scopes y authorizer aprobados. Para rollback, mantener
`wcs.agent-evaluation.trigger.enabled=false` y conservar el flujo actual y la
API read-only. No requiere recursos AWS nuevos ni cambios de migraciones.

## Evidencia requerida

Tests MockMvc de scopes y errores, tests de aplicación del orden
authorization-guard-executor, integración PostgreSQL/Testcontainers para la
key única, logs sanitizados y `mvn -B -q verify`.
