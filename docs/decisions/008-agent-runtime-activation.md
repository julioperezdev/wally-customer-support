# ADR-008 — Activación opcional de agentes desde el runtime

- Status: `Accepted`
- Related Jira: `WCS-50`
- Related ADRs: [`005-agent-registry-contract.md`](005-agent-registry-contract.md), [`006-agent-registry-persistence.md`](006-agent-registry-persistence.md), [`007-agent-activation-resolution.md`](007-agent-activation-resolution.md)

## Contexto

WCS ya cuenta con planes de ejecución tipados, persistencia del registry y un
resolver que distingue activaciones válidas de fallbacks. El runtime todavía
debe conservar el flujo determinístico actual mientras se incorpora esa
información de control plane de forma observable y reversible.

## Decisión

1. `ConversationContext` conserva el `Channel` interno proveniente del mensaje
   inbound; los constructores usados por clasificadores directos pueden dejarlo
   ausente.
2. `ConversationOrchestrator` consulta el agente lógico del primer paso del
   plan, junto con ambiente, canal y caso de uso.
3. La consulta está protegida por `wcs.agent-runtime.activation-enabled`, falsa
   por defecto. AppConfig podrá administrar el valor cuando exista aprobación.
4. Una activación `ACTIVE` sólo agrega `agentId` y `agentVersion` a eventos
   estructurados; no cambia todavía la implementación del caso de uso.
5. `NOT_CONFIGURED`, `DISABLED`, `KILL_SWITCH` y
   `REGISTRY_UNAVAILABLE` conservan la ejecución determinística actual.
6. La ausencia de canal impide resolver una activación y no interrumpe la
   respuesta.
7. Los eventos no contienen prompts, cuerpos de mensajes, secretos ni detalles
   de excepciones del registry.

## Consecuencias

- Se puede observar qué versión habría sido seleccionada antes de migrar la
  ejecución a agentes especializados.
- El cambio no requiere migración ni modifica los datos transaccionales.
- El contexto conserva compatibilidad con clasificadores invocados fuera de un
  canal HTTP.
- La activación productiva queda separada de este cambio de código: requiere
  habilitar la flag y contar con una activación persistida aprobada.

## Rollback

Volver `wcs.agent-runtime.activation-enabled` a `false` elimina la consulta al
registry desde el runtime y conserva la ejecución anterior. Si el código ya fue
desplegado, no hace falta eliminar la migración `V9` ni modificar activaciones
persistidas.
