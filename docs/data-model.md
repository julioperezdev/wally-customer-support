# Modelo de datos y schema — WCS

Owner: Tech Lead  
Status: `Proposed`  
Last reviewed: 2026-09-03
Related Jira: `WCS-13`, `WCS-14`, `WCS-15`, `WCS-16`, `WCS-25`, `WCS-28`, `WCS-29`
Related repository paths: `src/main/java/com/wally/customersupport/{conversation,catalog,support}/infrastructure/repository/postgres`, `src/main/resources/db/migration`

## Aislamiento en el RDS compartido

WCS utiliza la instancia PostgreSQL existente de `tesis-dev`, pero no comparte
tablas con ella. La configuración fija `wcs` y la migración inicial crean el
schema `wcs`; las entidades JPA y los nombres de las tablas están
calificados explícitamente con ese schema. Terraform sólo referencia el RDS y
su secret existente: no declara `aws_db_instance`, subnet groups ni cambios de
red en este repositorio.

## Entidades mínimas

### `conversations` — implementada en `V1__create_core_support_tables.sql`

- `id` UUID interno.
- `channel` y `external_conversation_id`, únicos como par.
- `external_customer_id`, aislado del ownership futuro por tienda y válido para cualquier canal.
- `status` (`OPEN`, `CLOSED`).
- timestamps.

### `messages` — implementada en `V1__create_core_support_tables.sql`

- `id` UUID interno.
- `conversation_id` FK.
- `channel`, redundante de forma intencional para mantener idempotencia por proveedor.
- `external_message_id`, único cuando existe.
- `direction` (`INBOUND`, `OUTBOUND`).
- `message_type` (`TEXT` en el MVP).
- `body` con política de retención definida.
- `message_type` (`TEXT` en la primera entrega).
- timestamps.

### `processing_attempts` — implementada en `V1__create_core_support_tables.sql`

- `id` UUID.
- `message_id` FK.
- `attempt_count` y `status`.
- error sanitizado.
- timestamps.

Esta entidad permite auditar reintentos sin sobrecargar la fila principal del mensaje.

### `outbox_messages` — implementada en `V1__create_core_support_tables.sql`

Permite confirmar el webhook después de una transacción local y despachar el trabajo de forma durable.

* `id` UUID.
* `aggregate_id` y `event_type`.
* `channel` y `recipient_id`, para enrutar la entrega al adapter correcto.
* campos de entrega tipados, sin payload Meta completo.
* `status` (`PENDING`, `PROCESSING`, `SENT`, `FAILED`).
* `attempts`, `available_at`, `version` y `sent_at`.
* timestamps.

La unicidad del `external_message_id` y el estado del outbox sobreviven a reinicios. `@Async` sin persistencia no es el mecanismo productivo.

### `catalog_products` y `catalog_variants` — implementadas en `V2`/`V3`

El catálogo demo de **Ropa de Programador** se separa en producto y variante:

* `catalog_products`: nombre, descripción, referencia `image_object_key` para
  un objeto futuro en S3, estado `active` y marca `demo`.
* `catalog_variants`: SKU único, talle, color, importe, moneda, stock y estado
  `active`.
* El acceso se realiza mediante `CatalogRepository` y filtros determinísticos
  por nombre, SKU, talle y color. El adapter no recibe SQL ni datos generados
  por el LLM.
* `V3` contiene sólo datos sintéticos versionados. La referencia S3 no implica
  que el MVP envíe imágenes como media por WhatsApp.

La migración incluye una constraint de stock no negativo y una unicidad por
producto, talle y color. No se modifica `V1`; el reemplazo de datos demo se
realiza con migraciones posteriores.

### `business_hours` y `support_policies` — implementadas en `V2`/`V3`

* `business_hours` guarda día ISO 1–7, horario, zona IANA, estado, marca demo y
  versión del registro. El dataset demo usa `America/Argentina/Buenos_Aires`,
  lunes a viernes 09:00–18:00, sábado 10:00–14:00 y domingo cerrado.
* `support_policies` guarda una clave estable, título, contenido, estado,
  marca demo, versión y fecha de publicación. El dataset inicial incluye
  `shipping`, `payments`, `changes` y `returns`.
* La aplicación consulta ambos grupos mediante
  `SupportConfigurationRepository`, dejando la evaluación de horario y la
  política de respuesta para el siguiente vertical slice conversacional.

Los registros de V3 son explícitamente DEMO y no constituyen la política legal
o comercial definitiva de la tienda.

### `knowledge_source` y `knowledge_document_version`

Se agregan cuando el producto acepta el alcance de conocimiento:

* fuente, owner, estado y política de acceso;
* versión de documento, checksum, fecha de publicación y expiración;
* status de ingestión e idempotency key;
* referencia al índice/provider, sin guardar secretos.

Si se elige pgvector, la columna vectorial se agrega en una migración posterior cuando estén aprobados modelo de embeddings y dimensión. Knowledge Bases no requiere almacenar embeddings en WCS.

### Entidades futuras

- `store`/`store_id` para aislamiento multi-tienda; el MVP mantiene una tienda
  demo y no usa `external_customer_id` como ownership.
- `human_handoff`.
- `knowledge_source` y versiones de contenido.
- `ai_usage_metric`.

## Reglas

- Las migraciones Flyway son inmutables una vez aplicadas.
- Toda migración nueva tiene rollback documentado o procedimiento reversible.
- No almacenar tokens de Meta ni keys de LLM en la BD.
- PII, retención, borrado y acceso deben estar definidos antes de producción.
- El aislamiento por tienda se debe diseñar antes de habilitar multi-tenant; no se debe asumir que un identificador externo de canal alcanza como ownership.
- La implementación inicial puede operar con una tienda, pero las claves internas deben permitir incorporar `store_id`/`account_id` sin redefinir un identificador de canal como ownership.
- JPA/Hibernate no reemplaza Flyway: el schema productivo se versiona con migraciones explícitas.
- No persistir payloads completos de Meta, prompts completos ni respuestas del proveedor salvo que exista una política de retención aprobada.
