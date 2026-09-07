# ADR-006 — Persistencia del registry de agentes

- Owner: Product/Tech Lead
- Status: `Accepted`
- Related Jira: `WCS-48`
- Related decision: [`005-agent-registry-contract.md`](005-agent-registry-contract.md)
- Related model: [`data-model.md`](../data-model.md)

## Contexto

WCS-47 definió un contrato inmutable para versiones de agentes y referencias
de activación. El siguiente paso necesita una implementación persistente para
poder consultar versiones y activaciones sin acoplar todavía el runtime
conversacional a un registry activo.

## Decisión

PostgreSQL, dentro del schema `wcs`, es la primera implementación del control
plane:

1. `agent_versions` tiene una clave lógica única `agent_id + agent_version` y
   conserva el artefacto completo de una versión, incluyendo los límites y la
   metadata de aprobación.
2. `agent_activations` conserva cada cambio de puntero por ambiente, canal y
   caso de uso. No se eliminan activaciones anteriores.
3. `allowed_tools` y `knowledge_sources` se guardan como JSONB estructurado.
4. La foreign key de activaciones impide apuntar a una versión inexistente.
5. La capa `AgentRegistryRepository` sólo expone modelos de dominio; JPA y
   Flyway quedan aislados en infraestructura.
6. El adapter rechaza sobrescrituras de la misma versión. La unicidad de la
   base protege adicionalmente frente a concurrencia.
7. La lectura de activación toma el último registro para la combinación de
   ambiente/canal/caso de uso y luego aplica `enabled`/`killSwitch`. Por eso un
   kill switch puede deshabilitar efectivamente una activación anterior sin
   borrar su historial.

## Seguridad y límites

- No se almacenan prompts completos, secretos, tokens, conversaciones ni PII
  de canales.
- El modelo guarda únicamente `systemPromptVersion` y un hash SHA-256.
- No se habilita SQL generado por LLM ni MCP.
- La migración nueva es `V9__create_agent_registry.sql`; las migraciones
  anteriores no se editan.
- Esta fase no publica activaciones en AppConfig ni cambia el comportamiento
  del `ConversationOrchestrator`.

## Consecuencias

La plataforma ya tiene una base persistente auditable para la próxima API de
backoffice y el adapter de feature flags. La fuente operativa todavía no se
consume desde el runtime; conectar la selección de agente requiere contratos
de aplicación, permisos, evaluación y rollout en tareas posteriores.
