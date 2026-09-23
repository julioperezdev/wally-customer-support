# Modelo de datos y schema — WCS

Owner: Tech Lead  
Status: `Proposed`  
Last reviewed: 2026-09-13
Related Jira: `WCS-13`, `WCS-14`, `WCS-15`, `WCS-16`, `WCS-25`, `WCS-28`, `WCS-29`, `WCS-33`, `WCS-34`, `WCS-35`, `WCS-37`, `WCS-47`, `WCS-48`, `WCS-51`, `WCS-60`, `WCS-61`, `WCS-62`, `WCS-63`, `WCS-64`, `WCS-65`, `WCS-66`, `WCS-67`, `WCS-68`, `WCS-69`, `WCS-70`, `WCS-122`, `WCS-129`
Related repository paths: `src/main/java/com/wally/customersupport/{conversation,catalog,support,agent}/infrastructure/repository/postgres`, `src/main/resources/db/migration`

## Aislamiento en el RDS compartido

WCS utiliza la instancia PostgreSQL existente de `tesis-dev`, pero no comparte
tablas con ella. La configuración fija `wcs` y la migración inicial crean el
schema `wcs`; las entidades JPA y los nombres de las tablas están
calificados explícitamente con ese schema. Terraform sólo referencia el RDS y
su secret existente: no declara `aws_db_instance`, subnet groups ni cambios de
red en este repositorio.

## Entidades mínimas

### `agent_evaluation_trigger_claims` — implementada en `V11__create_agent_evaluation_trigger_claims.sql`

- `id` UUID interno de la claim.
- `key_hash` SHA-256 hexadecimal de la idempotency key, único y no reversible
  en el contrato de almacenamiento.
- `claimed_at` timestamp de creación administrado por PostgreSQL.
- La inserción usa `ON CONFLICT DO NOTHING`, por lo que los reintentos no
  crean una segunda claim ni vuelven idempotente una key diferente por error.
- La tabla no guarda la key original, actor, prompts, respuestas, tokens,
  secretos ni PII. No se purga automáticamente en esta fase.

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

### `processing_attempts` — implementada en `V1__create_core_support_tables.sql` y `V14__make_inbound_processing_durable.sql`

- `id` UUID.
- `message_id` FK.
- `attempt_count` y `status` (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`).
- `available_at` para reintentos diferidos y `started_at` para recuperar leases
  abandonados después de un reinicio.
- error sanitizado, sin mensaje completo del proveedor ni payload.
- timestamps.

Esta entidad es también la cola durable de inbound. El webhook crea el mensaje
y una fila `PENDING` en la misma transacción; un worker reclama sólo el primer
trabajo pendiente de cada conversación, procesa fuera del request y marca el
resultado en una transacción con el outbox. La reclamación usa una actualización
condicional para que dos instancias no procesen el mismo trabajo. Un lease
vencido vuelve a `PENDING`; un mensaje no adelanta a otro anterior de la misma
conversación aunque esté esperando un retry.

### `outbox_messages` — implementada en `V1__create_core_support_tables.sql`

Permite confirmar el webhook después de una transacción local y despachar el trabajo de forma durable.

* `id` UUID.
* `aggregate_id` y `event_type`.
* `channel` y `recipient_id`, para enrutar la entrega al adapter correcto.
* campos de entrega tipados, sin payload Meta completo.
* `media_reference`, una referencia opaca opcional para una imagen de catálogo;
  no contiene una URL prefirmada y se agrega mediante `V21`.
* `status` (`PENDING`, `PROCESSING`, `SENT`, `FAILED`).
* `attempts`, `available_at`, `version` y `sent_at`.
* timestamps.

La unicidad del `external_message_id` y el estado del outbox sobreviven a
reinicios. El dispatcher reclama cada fila con una actualización condicional,
recupera estados `PROCESSING` antiguos y sólo entonces llama al adapter
outbound. `@Async` sin persistencia no es el mecanismo productivo.

### `catalog_products` y `catalog_variants` — implementadas en `V2`/`V3`

El catálogo demo de **Ropa de Programador** se separa en producto y variante:

* `catalog_products`: nombre, descripción, referencia `image_object_key` para
  una imagen fallback del producto, estado `active` y marca `demo`. Una
  referencia válida puede acompañar una coincidencia única con una entrega de
  imagen; la URL temporal se genera sólo en el despacho.
* `product_type`: tipo normalizado (`remera`, `buzo`, `campera` u `other`),
  utilizado para no confundir el tipo de prenda con el nombre del diseño.
* `catalog_variants`: SKU único, talle, color, importe, moneda, stock, estado
  `active` y referencia opcional `image_object_key`. La variante tiene
  prioridad sobre la imagen fallback del producto, por lo que dos colores del
  mismo producto pueden mostrar diseños distintos sin duplicar el producto.
* El acceso se realiza mediante `CatalogRepository` y filtros determinísticos
  por nombre, tipo, SKU, talle y color. El adapter no recibe SQL ni datos
  generados por el LLM.
* `V3` contiene sólo datos sintéticos versionados. Las referencias de sus datos
  sólo serán entregables si el objeto existe y respeta el prefijo configurado.

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

### `orders`, `order_items` y `payment_events` — implementadas en `V18`

El backoffice persiste el intento de venta y el estado del pago dentro del
schema `wcs`, sin mezclarlo con tablas de `tesis-dev`:

* `orders` identifica el pedido, cliente de referencia, estado, moneda, total,
  proveedor, preferencia, link, pago externo y una `idempotency_key` única;
* `order_items` guarda el snapshot de SKU, nombre, cantidad, precio, moneda y
  total de línea validado desde el catálogo al crear el pedido;
* `payment_events` conserva el evento sanitizado, hash del payload, estado
  consultado al proveedor y relación opcional con el pedido. La unicidad por
  proveedor y evento permite deduplicar reintentos del webhook.

La migración verifica stock bajo lock pesimista, pero esta versión no descuenta
ni reserva stock temporalmente. La reserva con expiración, descuentos,
reembolsos y compensaciones quedan para una migración posterior; no deben
inferirse a partir de un pedido `PENDING_PAYMENT`.

El mismo modelo se reutiliza para la compra conversacional. El caso de uso no
recibe precio, nombre ni moneda desde el mensaje o el LLM: resuelve una única
variante en PostgreSQL y delega en `OrderApplicationService`. El flujo y sus
respuestas están documentados en
[`conversational-checkout.md`](conversational-checkout.md).

### `carts` y `cart_items` — implementadas en `V22` (WCS-129)

El carrito conversacional es un agregado persistido por conversación:

* `carts` mantiene el actor pseudonimizado, canal, moneda, estado, versión de
  negocio y el pedido pendiente asociado al checkout;
* `cart_items` mantiene una línea por SKU y cantidad, con unicidad por carrito;
* `orders.cart_id` y `orders.cart_version` permiten reconstruir qué versión del
  carrito originó el pedido.

El carrito no es fuente de verdad para nombre, precio o stock: esos datos se
leen de `catalog_variants` al mostrar y al confirmar. La confirmación usa una
idempotency key derivada de `cart_id` y `cart_version`; cambiar el carrito
produce una nueva versión y una nueva operación. Cancelar el checkout marca
los pedidos pendientes como `CANCELLED`, reabre el carrito y evita que un
webhook posterior reactive un pedido terminal.

El detalle conversacional está en [`conversational-cart.md`](conversational-cart.md).

### `knowledge_source` y `knowledge_document_version`

Se agregan cuando el producto acepta el alcance de conocimiento:

* fuente, owner, estado y política de acceso;
* versión de documento, checksum, fecha de publicación y expiración;
* status de ingestión e idempotency key;
* referencia al índice/provider, sin guardar secretos.

Si se elige pgvector, la columna vectorial se agrega en una migración posterior cuando estén aprobados modelo de embeddings y dimensión. Knowledge Bases no requiere almacenar embeddings en WCS.

### Estado conversacional — contrato en `WCS-34`, persistencia en `WCS-35`, resumen en `WCS-36` y selección en `WCS-135`

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
* `selection_context` JSONB con la selección activa tipada: intención, acción,
  filtros de catálogo, SKU seleccionado y etapa conversacional. Su objeto
  `workingMemory` conserva hasta 10 referencias recientes a candidatos (nombre,
  SKU, talle y color), foco sólo cuando hay una variante inequívoca, el estado
  tipado de la última observación y timestamp. No copia imagen, precio ni stock.

La carga elimina de forma transaccional un estado vencido. El guardado valida
ownership y versión; una versión obsoleta se rechaza como conflicto. La tabla
se crea con `V6__create_conversation_memory_states.sql` y se amplía con
`V7__add_conversation_summary.sql` y `V24__add_conversation_selection_context.sql`.
El contrato JSON de persistencia es propio de infraestructura y no serializa
los métodos calculados del dominio. El adapter se puede activar sólo con
`wcs.conversation.memory.enabled=true`; el resumen adicional permanece
desactivado por defecto.

La selección conserva únicamente contexto de la conversación y no es fuente
de verdad para stock, precio, carrito ni pedidos. Los datos dinámicos siguen
resolviéndose en PostgreSQL mediante los servicios de catálogo y checkout.
El contrato JSON es compatible con registros anteriores: un `selection_context`
sin `workingMemory` se carga como memoria vacía; los campos legacy que ya no
participan en la resolución se ignoran al leer y se omiten al escribir. Una
observación de catálogo sin coincidencias
limpia candidatos previos para impedir que una referencia posterior los use;
antes de mutar el carrito se vuelve a validar el SKU contra PostgreSQL y el
stock/precio vigentes. No requiere nueva migración Flyway.

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

### Seguimiento humano y supresión de contacto — contrato en `WCS-26`, persistencia en `V15`/`V16`

`wcs.human_follow_up_tasks` es la cola durable que un backoffice presente o
futuro puede consumir. La primera entrega no incluye panel ni asignación de
agentes, pero deja un contrato tipado y consultable:

* `conversation_id` mantiene el ownership de la tarea;
* `source_message_id` referencia el evento que originó la derivación y permite
  idempotencia por `source_message_id + reason`;
* `reason` distingue `HUMAN_REQUEST`, `LOW_CONFIDENCE` y `UNRESOLVED`;
* `priority` usa `HIGH`, `NORMAL` o `LOW`; una solicitud explícita de persona
  se crea como `HIGH`;
* `status` usa `OPEN`, `IN_PROGRESS`, `DONE` o `CANCELLED`;
* `assigned_to` contiene únicamente la clave técnica del operador que tomó la
  tarea; `claim`, `release` y `resolve` usan actualizaciones condicionales para
  preservar ownership;
* `due_at` se calcula inicialmente a 24 horas, sin convertirlo en una promesa
  de SLA legal;
* se guardan timestamps y no se persiste el cuerpo del mensaje ni un resumen
  libre potencialmente identificable.

`wcs.contact_suppressions` contiene una fila por actor y guarda solamente un
`actor_key` SHA-256 de `channel + externalCustomerId`, el estado
`DO_NOT_CONTACT`, el motivo, el mensaje origen y timestamps. No se replica el
teléfono o identificador externo en la lista. `BAJA`, `STOP` y frases
equivalentes se detectan antes de memoria, preferencias y LLM; la operación
limpia el estado conversacional, no crea outbox y los reintentos son seguros.

La migración `V15__create_human_follow_up_and_contact_suppression.sql` no
modifica migraciones aplicadas. Las referencias a mensajes usan `ON DELETE SET
NULL` para que la retención pueda eliminar metadatos sin destruir la tarea.

`V16__add_backoffice_operations.sql` agrega `assigned_to` y la tabla
`wcs.catalog_stock_adjustments`. Cada ajuste guarda sólo SKU, stock anterior,
delta, stock resultante, motivo, actor técnico, clave idempotente y timestamp.
La variante usa una versión JPA y lock pesimista para evitar perder ajustes
concurrentes. `V26__add_catalog_variant_media.sql` agrega la referencia por
variante y conserva `catalog_products.image_object_key` como fallback para
productos antiguos o variantes que todavía no tienen media propia. Las keys
apuntan al bucket privado de S3 y nunca se envían al cliente final: el adapter
genera una URL prefirmada sólo al entregar la imagen.

### Retención operativa — job en `WCS-26`

`ConversationRetentionCleanupService` redacciona el cuerpo de mensajes después
de 30 días y elimina filas de mensaje y sus intentos de procesamiento después
de 90 días, en lotes acotados. Las métricas agregadas y los logs no son tocados
por este job. La ejecución queda desactivada por defecto hasta aprobar la
política legal/comercial:

```text
wcs.conversation.retention.enabled=false
wcs.conversation.retention.content-retention=PT720H
wcs.conversation.retention.metadata-retention=PT2160H
wcs.conversation.retention.aggregate-metrics-retention=PT8760H
wcs.conversation.retention.cleanup-batch-size=500
wcs.conversation.retention.schedule-delay-ms=86400000
```

### Registry de agentes y activaciones — contrato en `WCS-47`, persistencia en `V9`/`WCS-48`, perfiles Bedrock en `V27`/`WCS-140`

El control plane inicial persiste dos grupos separados:

* `wcs.agent_versions`: una fila por `agent_id + agent_version`, con estado de
  lifecycle, SemVer, proveedor/modelo, cuerpos de system/user prompt, schemas,
  parámetros de inferencia, pricing versionado, allowlists, políticas, límites,
  presupuesto y metadatos de aprobación;
* `wcs.agent_activations`: referencias auditables por ambiente, canal y caso de
  uso, con motivo, rollout, actor, timestamp, versión anterior y kill switch.

Las allowlists se almacenan como JSONB porque son colecciones estructuradas y
no deben convertirse en texto libre. La foreign key de la activación sólo
permite apuntar a una versión existente. La activación más reciente es la que
determina si existe una referencia activa; una activación con kill switch
oculta las anteriores sin borrar historial.

La migración `V9__create_agent_registry.sql` es nueva y no modifica las
migraciones aplicadas. `AgentRegistryRepository` expone lecturas y escrituras
tipadas; el adapter rechaza sobrescribir una versión ya persistida.
La persistencia se consulta desde el `ConversationOrchestrator` cuando el gate
de runtime está habilitado. La escritura de authoring y activaciones continúa
cerrada por flags independientes y el backoffice sólo la expone después de
pasar autenticación, scopes e idempotencia.

`AgentActivationResolver` agrega una frontera de lectura sin mutar el estado:
para una clave de agente, ambiente, canal y caso de uso devuelve la referencia
exacta o un fallback sin `agentId` ni `agentVersion`. `NOT_CONFIGURED`,
`DISABLED`, `KILL_SWITCH` y `REGISTRY_UNAVAILABLE` son razones controladas y
no contienen PII. `AgentRuntimeDefinitionResolver` agrega la segunda frontera:
carga la versión exacta, valida que sea publicable y devuelve un snapshot
inmutable de modelo, configuración ejecutable, límites, contratos y allowlists.
Los cuerpos de prompt sólo están en estas versiones autenticadas del control
plane; no se copian a tablas transaccionales, logs ni trazas. Los fallbacks
`VERSION_NOT_FOUND`, `VERSION_MISMATCH`, `VERSION_NOT_PUBLISHABLE`,
`INVALID_DEFINITION` y `REGISTRY_UNAVAILABLE` no interrumpen el flujo actual.
Cuando la activación está habilitada, la generación grounded de soporte recibe
ese snapshot y el adapter Bedrock valida el hash del prompt antes de aplicar
el modelo y sus límites. El catálogo sigue usando su tool determinística; la
migración del resto de steps del plan queda para una fase posterior.

WCS-120 agrega en `V19__add_agent_audit_and_execution_traces.sql`:

* `wcs.agent_registry_audit_events`: cambios de lifecycle, activaciones, kill
  switch y rollback con actor técnico, motivo, estados y alcance;
* `wcs.agent_execution_traces`: evidencia de cada ejecución con ruta,
  ambiente, canal, caso de uso, agente/versión, resultado, latencia, modelo y
  actor pseudónimo.

Ambas tablas excluyen prompts, respuestas, mensajes y secretos. La traza no
rompe la respuesta al cliente si la persistencia falla; en ese caso queda el
evento estructurado de observabilidad y se registra el error técnico.

### Resultados de evaluación — contrato en `WCS-60`, persistencia en `V10`/`WCS-61`

La evaluación se almacena como dos grupos relacionados y sólo después de una
ejecución completada:

* `wcs.agent_evaluation_runs`: `id`/`runId`, dataset y versión, agente y
  versión, proveedor/modelo, timestamps, duración, conteos, pass rate, score
  promedio y razones de fallo agregadas;
* `wcs.agent_evaluation_scenario_results`: run, `scenario_id`, versión,
  pass/fail, score, razones sanitizadas y metadata opcional de ejecución
  (latencia, tokens, costo, pricing version, intención y acción del router).
  `execution_routed_action` conserva la acción estructurada para comparar
  versiones sin persistir mensajes ni respuestas.

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
WCS-67 modela la evidencia del gate en memoria; no persiste aprobaciones ni
introduce campos de auditoría en PostgreSQL.
WCS-68 mantiene la autorización técnica como un contrato en memoria; no
persiste tokens, claims ni idempotency keys.

### Otras entidades futuras

- `store`/`store_id` para aislamiento multi-tienda; el MVP mantiene una tienda
  demo y no usa `external_customer_id` como ownership.
- `human_handoff`.
- `knowledge_source` y versiones de contenido.
- `ai_usage_metric`.

### Evolución del modelo y próximos pasos

La evolución no reemplaza el schema actual de una sola vez. El plan está
documentado en
[`conversational-platform-refactor-roadmap.md`](conversational-platform-refactor-roadmap.md)
y la primera etapa de selección conversacional se implementa en `V24`. Las
etapas siguientes deben agregar nuevas migraciones Flyway, probarse con
PostgreSQL/Testcontainers y mantener separadas las entidades dinámicas de
catálogo, carrito y pedidos de la memoria resumida de la conversación.

El objetivo es permitir refinamientos como “la M”, “ese en negro” o “agrega
otro” con contexto acotado y trazable, sin guardar prompts, respuestas
completas ni PII innecesaria.

## Reglas

- Las migraciones Flyway son inmutables una vez aplicadas.
- Toda migración nueva tiene rollback documentado o procedimiento reversible.
- No almacenar tokens de Meta ni keys de LLM en la BD.
- PII, retención, borrado y acceso deben estar definidos antes de producción.
- El aislamiento por tienda se debe diseñar antes de habilitar multi-tenant; no se debe asumir que un identificador externo de canal alcanza como ownership.
- La implementación inicial puede operar con una tienda, pero las claves internas deben permitir incorporar `store_id`/`account_id` sin redefinir un identificador de canal como ownership.
- JPA/Hibernate no reemplaza Flyway: el schema productivo se versiona con migraciones explícitas.
- No persistir payloads completos de Meta ni respuestas del proveedor.
- Los cuerpos de prompts se guardan únicamente en versiones inmutables del
  control plane (`agent_versions`) con acceso protegido; nunca en AppConfig,
  logs, trazas o tablas transaccionales de conversación.
