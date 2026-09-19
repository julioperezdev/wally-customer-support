# Inventario y contratos de routing de IA — WCS

Owner: AI/Tech Lead
Status: `Accepted`
Last reviewed: 2026-09-18
Related Jira: `WCS-130`, `WCS-131`, `WCS-133`, `WCS-137`
Related decisions: [`ADR-038`](decisions/038-internal-tool-routing-without-mcp.md), [`ADR-039`](decisions/039-specialist-tool-contracts.md)

Este documento cierra las cuatro fases del análisis de routing de tools sin
incorporar MCP al runtime de WCS:

1. inventario del sistema actual;
2. contratos de capacidades y decisiones;
3. capa interna de tools tipadas sobre los casos de uso existentes;
4. tool calling estructurado de Bedrock, opt-in y con validación posterior en
   el backend.

## Fase 1 — Inventario

### Intenciones y operaciones

La fuente de verdad del router es el código de dominio, no el prompt:

| Intención | Operaciones posibles | Fuente o dueño | Riesgo |
| --- | --- | --- | --- |
| `GREETING` | `GREETING` | respuesta determinística | bajo |
| `CATALOG_SEARCH` | `CATALOG_SEARCH`, `ADD_TO_CART`, `VIEW_CART`, `REMOVE_FROM_CART`, `CLEAR_CART`, `REVIEW_CHECKOUT` | `CatalogSpecialistExecutor`, `CatalogSearchTool`, carrito | medio |
| `PURCHASE_LINK` | `PURCHASE_LINK`, `CONFIRM_CHECKOUT` | checkout, pedido idempotente y adapter de pagos | alto |
| `BUSINESS_HOURS` | `BUSINESS_HOURS` | Knowledge Base | bajo |
| `POLICY_QUERY` | `POLICY_QUERY` | Knowledge Base | bajo/medio |
| `GENERAL_SUPPORT` | `GENERAL_SUPPORT` | Knowledge Base y humanizer | bajo |
| `HUMAN_HANDOFF` | `HUMAN_HANDOFF` | tarea de seguimiento humano | medio |
| `UNKNOWN` | `UNKNOWN` | fallback seguro | bajo |

El modelo puede proponer `intent`, `action`, `confidence`, `quantity`,
`catalogQuery`, `policyKey` y `missingParameters`. No puede proponer SQL,
clases Java, nombres de repositorios, credenciales ni endpoints arbitrarios.

### Agentes y prompts actuales

| Componente | Responsabilidad | Input acotado | Output |
| --- | --- | --- | --- |
| `conversation-intent-v4` | interpretar lenguaje natural y continuidad | historial reciente, resumen y preferencias sanitizadas | decisión estructurada de intención |
| `catalog-specialist` | ejecutar búsqueda dinámica | `CatalogQuery`, mensajes recientes y mensaje actual | hechos de producto, variantes, precio y stock |
| `knowledge-specialist` | responder conocimiento estático | consulta y contexto recuperado | respuesta grounded o fallback |
| `checkout-specialist` | mantener carrito y preparar checkout | SKU, cantidad, carrito y confirmación explícita | resumen, pedido idempotente o link |
| `conversation-response-v1` | humanizar una respuesta ya autorizada | hechos estructurados y contexto limitado | texto o media permitido |
| `support-safety` | aplicar política de seguridad | respuesta candidata y hechos autorizados | respuesta aceptada o fallback |

El modelo nunca es la fuente de verdad para stock, precio, políticas, estado de
pedido o pago. Esos datos provienen de PostgreSQL, Knowledge Base o un adapter
externo validado.

### Fuentes dinámicas y estáticas

- PostgreSQL: catálogo, variantes, precio, stock, carrito, pedidos, pagos,
  memoria de conversación y registro de agentes.
- Bedrock Knowledge Bases: horarios, ubicación, envíos, cambios, devoluciones,
  FAQ y conocimiento estático de la tienda.
- Mercado Pago/Telegram/WhatsApp: adapters externos con idempotencia y
  validación de webhook.
- AppConfig: configuración no secreta y flags; Secrets Manager: tokens y
  credenciales.

La búsqueda dinámica se mantiene determinística. No se genera SQL desde el
mensaje del cliente ni desde la salida del LLM.

### Observabilidad y fallos conocidos

Eventos principales para explicar una interacción sin guardar la conversación:

- `INTENT_CLASSIFIED`, `INTENT_DETERMINISTIC_OVERRIDE`;
- `AGENT_ROUTED`, `AGENT_EXECUTION_STARTED`, `AGENT_EXECUTION_COMPLETED` y
  `AGENT_EXECUTION_FAILED`;
- `AI_USAGE_RECORDED` para tokens, costo estimado, modelo, prompt, latencia y
  resultado;
- `AI_TOOL_CALL_PROPOSED` y `AI_TOOL_CALL_FAILED` para tool calling;
- `CATALOG_SEARCH_COMPLETED`, `CONVERSATIONAL_CART` y
  `CONVERSATIONAL_PURCHASE_LINK`;
- `RAG_RETRIEVAL_RECORDED`, `RESPONSE_POLICY_APPLIED` y
  `RESPONSE_POLICY_FALLBACK`.

Los fallos observados que el diseño corrige son: categorías convertidas en
nombre de producto (`quiero un buzo`), pérdida de filtros explícitos durante
una refinación (`talle M`), JSON incompleto, respuesta sin grounding y
selección de una operación sensible sin confirmación.

## Fase 2 — Contratos

### Contrato de una tool

`WcsToolDescriptor` exige:

- `name`: identificador estable y único;
- `description`: propósito acotado, sin instrucciones ejecutables libres;
- `inputSchemaVersion` y `inputSchemaJson`;
- `outputSchemaVersion` y `outputSchemaJson`;
- `requiredCapability`: capacidad lógica necesaria para ejecutarla.

El `WcsToolRegistry` valida que los nombres no se repitan. El schema debe ser
JSON válido y de tipo objeto. La autorización efectiva se compone de la
allowlist de tools de la versión del agente y de la seguridad externa del
backoffice/runtime; el modelo no puede elevar permisos.

### Contrato de routing

`conversation.route` es un contrato de inferencia: no ejecuta una operación.
Su input estructurado está versionado como `conversation-route-input-v2` y
contiene únicamente los campos que el backend ya sabe validar. La primera
implementación permite:

- enums alineados con `ConversationIntent` y `ConversationAction`;
- cantidad entre 1 y 100;
- confianza entre 0 y 1;
- filtros de catálogo con precio no negativo;
- `policyKey` allow-listed;
- hasta ocho parámetros faltantes.

El parser de la aplicación vuelve a validar, normaliza y reconcilia la
propuesta. Un JSON inválido, campo desconocido, acción no soportada o confianza
insuficiente produce `UNKNOWN`/fallback seguro. Los casos sensibles requieren
además confirmación, ownership, stock e idempotencia.

### Modelo de error

Los errores de un provider no se exponen al cliente final. Se registran como
metadata sanitizada (`errorType`, etapa, operación, modelo y correlación) y el
flujo usa el fallback determinístico. Una tool no disponible, no autorizada o
con input inválido no se sustituye por SQL genérico ni por una tool distinta.

## Fase 3 — Tool layer

La capa está en
`src/main/java/com/wally/customersupport/conversation/application/tool/`.

WCS-137 agrega dos fronteras provider-neutral:

- `WcsToolContractCatalog` centraliza el nombre lógico de la tool, su capacidad
  y las versiones de los schemas de entrada y salida;
- `AgentSpecialistRegistry` centraliza los especialistas, sus casos de uso y la
  allowlist de tools que cada agente puede ejecutar.

Los contratos no ejecutables ya no usan un objeto vacío genérico: cada uno
declara campos obligatorios, enums y límites de cantidad/tamaño adecuados para
su responsabilidad. Esto permite validar una propuesta de Bedrock y mostrarla
en el backoffice antes de agregar el adapter de ejecución correspondiente.

Cada step de `ConversationExecutionPlan` transporta ahora el `toolName` y sus
versiones de schema. El resolver rechaza una definición con un agente
desconocido, una tool no permitida o un contrato inexistente antes de ejecutar
el plan. El orquestador aplica la misma validación al plan activo, para que una
propuesta del modelo no amplíe los permisos en runtime.

Los wrappers ejecutables actuales son `catalog.search`, `catalog.stock`,
`knowledge.retrieve` y `safe-fallback`:

- `catalog.search` delega en `CatalogConversationService`, por lo que conserva
  ownership, reglas de stock, normalización y repositorios existentes.
- `catalog.stock` delega en `CartCatalogReader` para consultar una única
  variante activa por SKU y diferencia `AVAILABLE`, `OUT_OF_STOCK` y
  `NOT_FOUND`.
- `knowledge.retrieve` delega en `KnowledgeRetriever` y sólo devuelve metadata
  de grounding (`GROUNDED`, `NO_EVIDENCE` o `ERROR`, cantidad y score promedio),
  no el texto recuperado.
- `safe-fallback` es determinístico y devuelve si una razón de error sugiere
  handoff, sin ejecutar ninguna operación externa.

Cada wrapper valida su input y registra `WCS_TOOL_EXECUTED` o
`WCS_TOOL_FAILED` con metadata sanitizada. `CatalogSpecialistExecutor` sólo usa
la búsqueda cuando la definición activa del agente lo permite y emite eventos
estructurados de inicio, finalización o fallback.

`conversation.route` es un contrato de clasificación y no se registra como
tool ejecutable porque no debe poder disparar un caso de uso por sí mismo.
Carrito, checkout y handoff ya tienen contratos declarados para validación y
trazabilidad, con schemas concretos de operación y resultado, pero mantienen
sus servicios existentes como frontera de ejecución hasta que se publiquen
wrappers equivalentes. No se agregan implementaciones ficticias ni SQL
generado por el modelo.

## Fase 4 — Bedrock Tool Use / Structured Outputs

`BedrockConverseClient` puede traducir el descriptor a `ToolConfiguration`,
enviar el schema a Bedrock Converse y extraer un único `ToolUseBlock`. El
clasificador parsea ese input con el mismo parser y aplica las validaciones
del backend. La propuesta del modelo no tiene autoridad de ejecución.

Los nombres lógicos de WCS pueden usar puntos, por ejemplo
`conversation.route`, pero Bedrock exige nombres de provider con caracteres
alfanuméricos, guion o guion bajo. El adapter los traduce de forma estable a
`conversation_route` y vuelve a exponer el nombre lógico en la trazabilidad.
La selección específica de una tool sólo se envía a familias de modelos que
la soportan; con GPT-OSS se deja la selección automática y el prompt exige una
única llamada a la tool. Así evitamos una incompatibilidad del provider sin
relajar la validación posterior.

La activación es explícita:

```properties
wcs.ai.structured-tool-calling.enabled=false
```

El default continúa en `false`. Para un piloto controlado se cambia a `true`
en AppConfig sólo después de verificar que el model ID, la región, el role IAM
y el contrato sean compatibles. Si Bedrock no devuelve la tool requerida, la
llamada falla de forma observable y el clasificador devuelve la decisión
segura; no hay fallback silencioso a otra operación.

Cada propuesta registra sólo metadata: nombre de tool, versiones de schema,
capacidad, cantidad y nombres de campos. No se registra el valor de los
argumentos porque puede contener PII o datos comerciales. El uso de tokens,
costo estimado, latencia y resultado queda en `AI_USAGE_RECORDED`.

Para rutas seguras y no mutantes (`GREETING`, `BUSINESS_HOURS`,
`POLICY_QUERY`, `GENERAL_SUPPORT` y `HUMAN_HANDOFF`), el parser puede aplicar
una confianza segura cuando el modelo devuelve cero o no informa el campo.
Las operaciones con efectos, como carrito, checkout y pago, siguen exigiendo
una confianza explícita y suficiente.

### Evidencia de cierre

- `WcsToolRegistryTest`: unicidad y descubrimiento de contratos.
- `CatalogQueryParserTest`: precedencia de filtros explícitos y continuidad.
- `BedrockConverseClientTest`: schema, tool choice, extracción estructurada,
  uso y sanitización de logs.
- `BedrockConversationIntentClassifierTest`: modo legacy y modo estructurado.
- `mvn -B verify`: debe cerrar la suite completa antes de publicar una imagen.

## Fuera de alcance deliberado

- MCP dentro del runtime de WCS;
- un mega-tool `query_database`;
- SQL generado por el LLM;
- ejecución de una tool sólo porque el modelo la nombró;
- activar Tool Use en producción sin piloto, métricas y rollback.
