# Modelo de datos y schema — WCS

Owner: Tech Lead  
Status: `Proposed`  
Last reviewed: 2026-08-30  
Related Jira: `WCS-13`, `WCS-14`, `WCS-15`, `WCS-16`  
Related repository paths: `src/main/java/.../adapter/out/persistence`, `src/main/resources/db/migration`

## Entidades mínimas

### `conversation`

- `id` UUID interno.
- `wa_id` identificador de WhatsApp, único.
- `last_inbound_at`.
- `service_window_expires_at`.
- `status` (`OPEN`, `ESCALATED`, `CLOSED`).
- timestamps.

### `message`

- `id` UUID interno.
- `conversation_id` FK.
- `external_message_id`, único cuando existe.
- `direction` (`INBOUND`, `OUTBOUND`).
- `message_type` (`TEXT` en el MVP).
- `body` con política de retención definida.
- `processing_status` (`RECEIVED`, `QUEUED`, `PROCESSING`, `SENT`, `FAILED`, `IGNORED`).
- `attempt_count`.
- `provider_message_id`.
- `error_code` sin secretos.
- timestamps.

### `message_processing_attempt`

- `id` UUID.
- `message_id` FK.
- `attempt_number`.
- `processor` (`LLM`, `WHATSAPP`).
- `status`, duración y código de error.
- `request_id`.
- timestamps.

Esta entidad permite auditar reintentos sin sobrecargar la fila principal del mensaje.

### `message_outbox`

Permite confirmar el webhook después de una transacción local y despachar el trabajo de forma durable.

* `id` UUID.
* `aggregate_type`, `aggregate_id`.
* `event_type` (`MESSAGE_RECEIVED`, `MESSAGE_PROCESS` o equivalente).
* `payload_reference` sin payload sensible completo.
* `status` (`PENDING`, `PROCESSING`, `SENT`, `FAILED`).
* `attempt_count`, `next_attempt_at` y `locked_until`.
* timestamps.

La unicidad del `external_message_id` y el estado del outbox deben sobrevivir a reinicios. `@Async` sin persistencia no es el mecanismo productivo.

### `knowledge_source` y `knowledge_document_version`

Se agregan cuando el producto acepta el alcance de conocimiento:

* fuente, owner, estado y política de acceso;
* versión de documento, checksum, fecha de publicación y expiración;
* status de ingestión e idempotency key;
* referencia al índice/provider, sin guardar secretos.

Si se elige pgvector, la columna vectorial se agrega en una migración posterior cuando estén aprobados modelo de embeddings y dimensión. Knowledge Bases no requiere almacenar embeddings en WCS.

### Entidades futuras

- `support_policy` / catálogo de respuestas autorizadas.
- `human_handoff`.
- `knowledge_source` y versiones de contenido.
- `ai_usage_metric`.

## Reglas

- Las migraciones Flyway son inmutables una vez aplicadas.
- Toda migración nueva tiene rollback documentado o procedimiento reversible.
- No almacenar tokens de Meta ni keys de LLM en la BD.
- PII, retención, borrado y acceso deben estar definidos antes de producción.
- El aislamiento por tienda se debe diseñar antes de habilitar multi-tenant; no se debe asumir que `wa_id` alcanza como ownership.
- La implementación inicial puede operar con una tienda, pero las claves internas deben permitir incorporar `store_id`/`account_id` sin redefinir `wa_id` como ownership.
- JPA/Hibernate no reemplaza Flyway: el schema productivo se versiona con migraciones explícitas.
- No persistir payloads completos de Meta, prompts completos ni respuestas del proveedor salvo que exista una política de retención aprobada.
