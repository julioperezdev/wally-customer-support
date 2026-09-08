# Modelo de datos y schema — WCS

Owner: Tech Lead  
Status: `Proposed`  
Last reviewed: 2026-09-06
Related Jira: `WCS-13`, `WCS-14`, `WCS-15`, `WCS-16`, `WCS-25`, `WCS-28`, `WCS-29`, `WCS-33`, `WCS-34`, `WCS-35`, `WCS-37`, `WCS-47`, `WCS-48`, `WCS-51`, `WCS-60`, `WCS-61`, `WCS-62`, `WCS-63`, `WCS-64`, `WCS-65`, `WCS-66`
Related repository paths: `src/main/java/com/wally/customersupport/{conversation,catalog,support,agent}/infrastructure/repository/postgres`, `src/main/resources/db/migration`

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
* `product_type`: tipo normalizado (`remera`, `buzo`, `campera` u `other`),
  utilizado para no confundir el tipo de prenda con el nombre del diseño.
* `catalog_variants`: SKU único, talle, color, importe, moneda, stock y estado
  `active`.
* El acceso se realiza mediante `CatalogRepository` y filtros determinísticos
  por nombre, tipo, SKU, talle y color. El adapter no recibe SQL ni datos
  generados por el LLM.
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

### Estado conversacional — contrato en `WCS-34`, persistencia en `WCS-35` y resumen en `WCS-36`

La primera entrega reconstruye los filtros activos a partir de una ventana
acotada de mensajes inbound persistidos. `WCS-34` define el contrato
`ConversationMemory`, el modelo inmutable `ConversationState` y los límites de
privacidad/retención. `WCS-35` persiste el estado tipado por conversación,
separado del historial, mediante la tabla `wcs.conversation_memory_states`:

* `conversation_id` UUID como clave y referencia a `wcs.conversations`;
* `actor_id` pseudónimo para ownership y aislamiento;
* `recent_messages` JSONB con la ventana normalizada;
* `updated_at` para aplicar el TTL;
* `version` para evitar que dos turnos concurrentes mezclen contexto.
* `conversation_summary` opcional con el resumen del prefijo antiguo;
* `summary_version` y `summarized_message_count` como checkpoint versionado;
* `summary_updated_at` para aplicar la retención del estado completo.

La carga elimina de forma transaccional un estado vencido. El guardado valida
ownership y versión; una versión obsoleta se rechaza como conflicto. La tabla
se crea con `V6__create_conversation_memory_states.sql` y se amplía con
`V7__add_conversation_summary.sql`. El adapter se puede activar sólo con
`wcs.conversation.memory.enabled=true`; el resumen adicional permanece
desactivado por defecto.

El resumen conserva únicamente contexto conversacional y no será fuente de
verdad para filtros tipados, stock, precio, carrito ni pedidos.

### Preferencias explícitas — contrato en `WCS-37`, persistencia en `V8`

`wcs.customer_preferences` mantiene preferencias de bajo riesgo que el cliente
expresó o confirmó explícitamente. El modelo está separado de la memoria de
sesión para que una preferencia durable no se confunda con el filtro de una
consulta puntual:

* `actor_id` identifica al cliente de forma pseudónima y es obligatorio para
  ownership;
* `preference_key` y `preference_value` contienen valores normalizados y
  acotados por el servicio de aplicación;
* `preference_scope` distingue `ACTOR` de `CONVERSATION`;
* `confidence`, `origin`, `confirmed`, `updated_at` y `expires_at` permiten
  auditar el origen y aplicar TTL;
* índices únicos impiden duplicar una misma preferencia dentro de su alcance;
* la foreign key de `conversation_id` sólo aplica a preferencias de alcance
  `CONVERSATION`.

La primera implementación sólo admite la preferencia explícita de color
preferido (`preferred_color`) y no hace extracción automática desde texto. La
migración `V8__create_customer_preferences.sql` es reversible mediante el
procedimiento documentado en el propio archivo. El feature está desactivado
por defecto con `wcs.conversation.preferences.enabled=false`.

Las preferencias son contexto auxiliar: no pueden sobreescribir filtros
actuales ni ser autoridad para stock, precio, carrito, pedidos o acciones
sensibles.

### Registry de agentes y activaciones — contrato en `WCS-47`, persistencia en `V9`/`WCS-48`

El control plane inicial persiste dos grupos separados:

* `wcs.agent_versions`: una fila por `agent_id + agent_version`, con estado de
  lifecycle, modelo, parámetros acotados, hash/version de prompt, schemas,
  allowlists, políticas, límites, presupuesto y metadatos de aprobación;
* `wcs.agent_activations`: referencias auditables por ambiente, canal y caso de
  uso, con motivo, rollout, actor, timestamp, versión anterior y kill switch.

Las allowlists se almacenan como JSONB porque son colecciones estructuradas y
no deben convertirse en texto libre. La foreign key de la activación sólo
permite apuntar a una versión existente. La activación más reciente es la que
determina si existe una referencia activa; una activación con kill switch
oculta las anteriores sin borrar historial.

La migración `V9__create_agent_registry.sql` es nueva y no modifica las
migraciones aplicadas. `AgentRegistryRepository` expone lecturas y escrituras
tipadas; el adapter rechaza sobrescribir una versión ya persistida. La
persistencia todavía no está conectada al `ConversationOrchestrator`, a
AppConfig ni al backoffice.

`AgentActivationResolver` agrega una frontera de lectura sin mutar el estado:
para una clave de agente, ambiente, canal y caso de uso devuelve la referencia
exacta o un fallback sin `agentId` ni `agentVersion`. `NOT_CONFIGURED`,
`DISABLED`, `KILL_SWITCH` y `REGISTRY_UNAVAILABLE` son razones controladas y
no contienen PII. `AgentRuntimeDefinitionResolver` agrega la segunda frontera:
carga la versión exacta, valida que sea publicable y devuelve un snapshot
inmutable de modelo, límites, contratos y allowlists. El snapshot no persiste
ni contiene prompts; sólo conserva metadatos de prompt. Los fallbacks
`VERSION_NOT_FOUND`, `VERSION_MISMATCH`, `VERSION_NOT_PUBLISHABLE`,
`INVALID_DEFINITION` y `REGISTRY_UNAVAILABLE` no interrumpen el flujo actual.
La definición todavía no se usa para ejecutar el caso de uso desde el runtime.

### Resultados de evaluación — contrato en `WCS-60`, persistencia en `V10`/`WCS-61`

La evaluación se almacena como dos grupos relacionados y sólo después de una
ejecución completada:

* `wcs.agent_evaluation_runs`: `id`/`runId`, dataset y versión, agente y
  versión, proveedor/modelo, timestamps, duración, conteos, pass rate, score
  promedio y razones de fallo agregadas;
* `wcs.agent_evaluation_scenario_results`: run, `scenario_id`, versión,
  pass/fail, score, razones sanitizadas y metadata opcional de ejecución
  (latencia, tokens, costo y pricing version).

La relación tiene foreign key con borrado en cascada y unicidad de
`run_id + scenario_id`. Los scores, tiempos, tokens y costos tienen constraints
de rango; los índices permiten consultar histórico por agente/versión, dataset
y fecha. No existen columnas para prompts, respuestas, mensajes, teléfonos ni
PII. La migración `V10__create_agent_evaluation_results.sql` agrega estas
tablas sin modificar `V1`–`V9`.

El adapter `AgentEvaluationRunRepository` expone sólo `save` y lookup por
`runId`, y rechaza sobrescribir runs existentes. La retención y el control de
acceso del histórico se definirán antes de habilitar un job o backoffice
compartido. WCS-62 agrega la consulta interna tipada con filtros por identidad
versionada y fecha, páginas acotadas y orden estable por `completed_at`/`id`;
los listados devuelven resúmenes y el detalle continúa siendo sanitizado.
WCS-63 compara dos runs del mismo dataset sobre este contrato sin agregar
datos nuevos al esquema: el baseline y el candidate conservan su identidad y
los deltas operativos quedan no disponibles cuando falta metadata en alguno de
los lados. WCS-64 proyecta esa comparación a un envelope de exportación
versionado sin agregar tablas ni escribir datos derivados.
WCS-65 define la política y el estado de retención en memoria; el esquema no
agrega columnas, no se persisten decisiones y no se habilita ninguna purga.
WCS-66 revisa páginas de resúmenes en memoria y tampoco agrega columnas,
persistencia, escrituras o acciones de eliminación.

### Otras entidades futuras

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
- No guardar el contenido del prompt en `agent_versions`: sólo su versión y hash.
