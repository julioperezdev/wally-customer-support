# ADR-034 — Authoring, publicación y evidencia runtime de la plataforma de agentes

Status: accepted for WCS-120
Scope: bounded context `agent`, backoffice técnico y runtime conversacional

## Decision

WCS mantiene un registry PostgreSQL de definiciones inmutables. Editar un
agente significa clonar una versión y crear otra `DRAFT`; nunca se modifica el
contenido de una versión publicada. Cada versión referencia, sin incluir su
contenido, la versión y el hash de su prompt, los schemas de entrada/salida,
modelo, parámetros, herramientas, fuentes de conocimiento, política de
memoria y límites operativos.

El lifecycle operativo es:

`DRAFT -> CANDIDATE -> EVALUATED -> APPROVED -> ACTIVE -> RETIRED`

`EVALUATED`, `DEPRECATED` y `ROLLED_BACK` se conservan por compatibilidad con
la historia ya persistida. `ACTIVE` expresa una versión publicada en el
registry; la activación concreta sigue siendo una referencia separada por
`environment + channel + useCase`, con rollout, kill switch y rollback sin
borrar historial.

La publicación requiere el permiso independiente
`agent-registry.publish`, además de `agent-registry.write` para entrar a la
superficie de comandos. El rol `agent-operator` recibe ambos permisos y
`store-viewer` sólo puede leer. La flag de escritura continúa cerrada por
defecto hasta que el ambiente tenga su smoke test aprobado.

## Evidencia y privacidad

Cada creación, clonación, transición, activación, kill switch y rollback
escribe una fila de `wcs.agent_registry_audit_events`. La auditoría guarda
actor técnico, motivo, estados y alcance, pero no guarda prompts, secretos,
aprobaciones completas ni contenido conversacional.

Cada ejecución conversacional escribe una fila de
`wcs.agent_execution_traces` con la ruta, agente/versión resuelta, proveedor,
modelo, resultado, latencia, coste/tokens cuando estén disponibles, un
`correlationId` y un `actorKey` pseudónimo. No se persiste el mensaje del
cliente ni la respuesta.

## Contratos HTTP

Además del registry y activaciones existentes:

- `GET /internal/agent-registry/audit?agentId=&limit=` devuelve auditoría
  sanitizada y requiere `agent-registry.read`.
- `GET /internal/agent-registry/executions?agentId=&useCase=&limit=` devuelve
  trazas sanitizadas y requiere `agent-registry.read`.
- `POST /internal/agent-registry/agents/{agentId}/versions/{version}/lifecycle`
  acepta también `ACTIVE` y `RETIRED`; `APPROVED`, `ACTIVE` y `RETIRED`
  requieren `agent-registry.publish` y las referencias de aprobación exigidas
  por el servicio.

Los comandos conservan `Idempotency-Key` y el backend sigue siendo la
autoridad: la UI sólo construye requests y nunca decide permisos, estado ni
versión efectiva.

## Rollback

El rollback de tráfico continúa siendo no destructivo: `rollback` crea una
nueva referencia de activación hacia la versión anterior. El rollback de
authoring se realiza clonando la versión conocida y promoviendo la nueva
versión mediante el lifecycle. Para retirar inmediatamente una ruta se usa
kill switch o se deshabilita el runtime gate; ninguna de estas acciones borra
auditoría o trazas.

## Fuera de este slice

WCS-120 no permite editar prompts completos desde el navegador, generar SQL
con un LLM, conectar MCP a producción ni ejecutar herramientas arbitrarias.
Los artefactos de prompts y schemas permanecen fuera del payload HTTP y se
integrarán con el proveedor versionado aprobado en el siguiente slice.
