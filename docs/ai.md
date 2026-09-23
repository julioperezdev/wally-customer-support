# IA, LLM y RAG — WCS

Owner: AI/Tech Lead  
Status: `Accepted`
Last reviewed: 2026-09-08
Related Jira: `WCS-11`, `WCS-20`, `WCS-21`, `WCS-30`, `WCS-33`, `WCS-51`, `WCS-52`, `WCS-53`, `WCS-54`, `WCS-82`, `WCS-83`, `WCS-84`, `WCS-130`, `WCS-131`, `WCS-141`
Related repository paths: `src/main/java/com/wally/customersupport/conversation/infrastructure/ai`, `src/main/resources/prompts`, `src/test/resources/fixtures`

## Registro de modelos

| ID lógico | Proveedor | Uso | Estado |
| --- | --- | --- | --- |
| `llm.mock.v1` | Interno | Desarrollo, tests y fixtures | Accepted |
| `llm.bedrock.openai.gpt-oss-20b.v1` | AWS Bedrock | Clasificación de intención y soporte general | Accepted |
| `conversation-router-v3` | AWS Bedrock GPT-OSS 20B | Routing semántico, reasoning effort `medium` | Candidata local; evaluación pendiente |
| `llm.bedrock.nova-pro.v1` | AWS Bedrock | Referencia histórica para generación documental | Reference |

Bedrock se integra detrás de `ConversationIntentClassifier` y `LlmClient`.
`ConversationOrchestrator` sólo consume decisiones estructuradas del clasificador
y ejecuta casos de uso internos. El caso de uso no conoce el model ID ni el SDK.
La selección del modelo, región, límites, timeout, guardrails y fallback se
resuelve mediante AppConfig para el modelo general; el perfil del router se
versiona por separado en `application.properties`. El acceso a Bedrock se
autoriza con IAM.

Cada llamada a Bedrock Converse emite el evento estructurado
`AI_USAGE_RECORDED`, con etapa, operación, proveedor, model ID, éxito, tokens
de entrada/salida/total, latencia total, latencia reportada por el proveedor y
un costo USD estimado. El costo usa `wcs.ai.pricing-version` y los precios por
millón de tokens de entrada/salida definidos en AppConfig o en los defaults de
bootstrap. Nunca se registran prompts, respuestas ni secretos. El evento y sus
consultas están documentados en [`docs/observability.md`](observability.md) y
[`observability/grafana/queries/cloudwatch-logs-insights.md`](../observability/grafana/queries/cloudwatch-logs-insights.md).

Cuando existe una activación válida, la llamada de generación recibe el
snapshot de `AgentRuntimeDefinition`. En ese caso `AI_USAGE_RECORDED` agrega
`agentId`, `agentVersion`, `inputSchemaVersion`, `outputSchemaVersion`,
`promptVersion`, `promptHash`, límites y timeout de la versión ejecutada. El
adapter Bedrock selecciona el `modelId`, `temperature`, `topP` y
`maxOutputTokens` del snapshot; además exige que el hash del prompt resuelto
coincida con el hash publicado. Un mismatch produce fallback y no cambia
silenciosamente al prompt global. El router y el especialista de catálogo
continúan con sus fronteras actuales hasta migrar cada step del plan.

Las evaluaciones de agentes tienen un executor Bedrock opcional, separado del
runtime conversacional. Sólo recibe hechos sintéticos y validados del dataset,
no genera SQL ni consulta tools. `deterministic` es el default y rollback;
`bedrock` requiere activación explícita y resuelve el agent ID + versión SQL
inmutable directamente, sin depender de activaciones ni tocar tráfico. La
evaluación cubre `response-humanization` sobre `catalog-response-v1` y
`conversation-router` sobre `conversation-routing-v1` (31 casos sintéticos
contrastivos con historial). Usa el system prompt, template, model ID,
parámetros, límites, timeout y precios de la versión SQL seleccionada. El
router se puntúa por intención, acción y filtros; no se genera ni almacena una
respuesta textual. Se rechaza DRAFT, un prompt cuyo hash no coincida,
schemas/placeholders inválidos y perfiles que excedan los caps. Dos runs sobre
el mismo dataset versionado permiten comparar baseline y candidata. El modelo
no recibe conversaciones reales ni ejecuta SQL, tools, pedidos o acciones. El
histórico conserva número SQL, modelo, dataset y métricas; el evento
`AI_USAGE_RECORDED` añade SemVer y hash de prompt sin contenido.

## RAG

RAG se integra detrás de `KnowledgeRetriever` y se mantiene separado de `LlmClient`:

| ID lógico | Adapter | Uso | Estado |
| --- | --- | --- | --- |
| `knowledge.mock.v1` | Interno | Tests y desarrollo local | Accepted |
| `knowledge.bedrock-kb.s3-vectors.v1` | AWS Bedrock Knowledge Bases + S3 Vectors | Documentación estática de WCS | In implementation |
| `knowledge.pgvector.v1` | PostgreSQL + pgvector | Índice propio, control de chunks y filtros | Proposed |

La decisión actual es usar una Knowledge Base propia de WCS para conocimiento
documental y mantener pgvector como alternativa futura. Ambas implementaciones
deben entregar el mismo `RetrievedContext`, con source ID, versión, score y
fragmentos limitados. La ingestión, versionado y borrado de documentos se diseña
como un flujo separado de la consulta.

La implementación inicial de la Knowledge Base usa S3 como fuente,
Amazon S3 Vectors como vector store y Amazon Titan Text Embeddings V2 con
vectores `float32` de 1024 dimensiones. El bucket, índice, Knowledge Base y
service role deben ser exclusivos de WCS. El patrón histórico de
`bigg-rag-sales-offhours` se conserva sólo como referencia; sus documentos no
se reutilizan. Los documentos versionados están en `knowledge-base/wcs/` y la
ingesta se inicia de forma explícita después de revisar el contenido publicado.

S3 Vectors es apropiado para consultas documentales de baja frecuencia y
búsqueda semántica. Si WCS requiere búsqueda híbrida o filtros avanzados, se
reevaluará OpenSearch o un índice propio.

La memoria conversacional se diseña separada de RAG. El contexto de sesión y
los filtros activos pertenecen a WCS; AgentCore Memory sólo se evaluará como
adapter opcional después de una implementación inicial en PostgreSQL. El plan
completo está en
[`conversational-memory-and-agentcore-plan.md`](conversational-memory-and-agentcore-plan.md)
y la decisión en `ADR-003`.

Cada modelo real debe registrar proveedor, model ID, versión, límites, timeout, precio vigente, fecha de revisión y casos permitidos.

## Configuración ejecutable Bedrock versionada en SQL — WCS-140 / V27

Las llamadas generativas actuales obtienen su configuración desde la versión
activa del registry en PostgreSQL; se resuelve al empezar cada llamada, sin
cache, para que una activación/rollback tome efecto en la siguiente inferencia.
La migración crea una línea base `1.0.0` que copia la configuración actual de:

| Agent ID | Operación Bedrock | Caso de uso | Responsabilidad |
| --- | --- | --- | --- |
| `conversation-router` | `conversation.intent.classify` | `ROUTING` | Interpreta mensaje/contexto y produce una decisión WCS estructurada |
| `response-generation` | `conversation.reply.generate` | `GENERAL_SUPPORT` | Redacta soporte con contexto y conocimiento aprobado |
| `response-humanization` | `conversation.response.humanize` | `CATALOG_SEARCH` | Humaniza hechos determinísticos; el validador vigente sigue siendo obligatorio |
| `conversation-summarizer` | `conversation.summary.generate` | `CONVERSATION_SUMMARY` | Resume mensajes redactados para memoria, nunca datos transaccionales |

Cada versión inmutable guarda prompts system/user, schemas JSON,
`modelProvider/modelId`, temperatura, `topP`, esfuerzo de razonamiento,
límite de entrada/salida, timeout, presupuesto y pricing versionado con tarifas
input/output. El proveedor, región y credenciales siguen siendo configuración
de plataforma/IAM. Modelo y tarifas se publican juntos para que cambiar el
modelo no distorsione el costo estimado de Grafana.

`semanticVersion` usa MAJOR.MINOR.PATCH: primer baseline `1.0.0`; cambio pequeño
compatible `1.0.1`; cambio funcional compatible `1.1.0`; cambio incompatible de
responsabilidad/contrato `2.0.0`. `agent_version` sigue siendo la clave SQL.
Clonar genera una nueva DRAFT inmutable con PATCH siguiente; al crear la DRAFT
se puede seleccionar MINOR o MAJOR. Lifecycle, evaluación, aprobación y
activación por ambiente/canal/caso de uso continúan siendo gates obligatorios.
Rollback significa activar una versión anterior, nunca editar una versión
publicada.

El primer despliegue de WCS-140 incorpora código y `V27`; después, crear,
evaluar y activar nuevas versiones de esos agentes se refleja en el siguiente
llamado sin redeploy de API ni una key por agente en AppConfig. Los templates
interpolan sólo placeholders allowlisted; no evalúan código, SQL ni expresiones.
`maxInputTokens` se convierte a un límite aproximado de caracteres (`4
caracteres/token`) porque este adapter de Converse no fija un límite exacto de
tokens de entrada. Las métricas de Bedrock reportan el conteo real de tokens.

Los schemas se guardan como contrato versionado y su enforcement depende del
tipo de agente: el router mapea su schema de salida al contrato de tool y WCS
valida/parsa la decisión; respuesta, humanización y resumen conservan sus
validadores determinísticos, mientras que sus schemas son contrato de authoring
y evaluación. Un prompt no concede autoridad sobre tools, SQL, stock, precio,
pagos ni acciones sensibles.

Ante activación ausente o perfil inválido, WCS no ejecuta el perfil: emite un
evento sanitizado con agente/versión/razón y conserva el camino compatible. Los
eventos `AI_USAGE_RECORDED` incluyen agente, versión SQL, SemVer, modelo,
hash/version de prompt, tokens, pricing, costo, latencia y error sanitizado;
nunca incluyen prompts, inputs, respuestas, secretos ni PII. La decisión está
en [`ADR-043`](decisions/043-sql-agent-invocation-versions.md); `ADR-032` queda
como decisión histórica supersedida.

La comparación de versiones reutiliza el runner de evaluaciones existente:
usar el mismo agente lógico, dataset versionado y cobertura exacta de escenarios.
La comparación entrega deltas de pass rate, validez, grounding, seguridad y
utilidad, y agrega dimensiones de intención, entidades, tools o RAG sólo cuando
ambos runs las midieron sobre los mismos escenarios. El backoffice muestra una
conclusión descriptiva y diferencias por escenario; costo, tokens y latencia
siguen siendo señales operativas separadas de calidad. Runs incompatibles se
rechazan con un error específico y una suite con IDs duplicados no se empareja
de forma ambigua.

La suite `conversation-routing-v1` conserva el corpus histórico de 31 casos.
`conversation-routing-v2` conserva esas entradas, completa las expectativas de
filtros, agrega tres casos de cantidad de carrito y expone cantidad como métrica
separada. Un run V1 no se compara directamente con V2: cada versión SQL evaluada
debe quedar asociada al mismo dataset exacto que ejecuta.

La etiqueta de resultado (`QUALITY_IMPROVED`, `QUALITY_REGRESSION`, `MIXED` o
`NO_QUALITY_CHANGE`) no implica significancia estadística, aprobación ni
promoción automática. Con pocas preguntas, es una señal para revisar los casos
individuales y ampliar la evaluación antes de decidir una activación. Las
métricas o coberturas ausentes se muestran como no disponibles, nunca como
cero.

El criterio aplicado para ampliar el corpus, interpretar los resultados
locales v1–v4 y diseñar futuras comparaciones está en
[`conversation-routing-evaluation-method.md`](conversation-routing-evaluation-method.md).
En particular, los runs que usan versiones distintas de dataset no se deben
interpretar como una comparación causal de prompts.

La versión `1.0.1` del `response-humanization` agrega un bloque estructurado
`required_facts` al prompt, con nombre, SKU, color, talle, precio, moneda y
stock de cada producto. Esto reduce omisiones del modelo al redactar listas
largas; no reemplaza ni relaja el validador determinístico. Si Bedrock vuelve
a omitir un hecho, WCS conserva el fallback seguro. La migración `V28` activa
esta versión para `CATALOG_SEARCH` en Telegram y WhatsApp y deja `1.0.0`
disponible como rollback.

## Prompt de respuesta y límites del proveedor

Si no hay una configuración SQL activa y válida, los adapters conservan el
prompt registry existente como compatibilidad/fallback. AppConfig sigue
controlando configuración general no sensible y límites globales de seguridad;
Secrets Manager y el role IAM siguen siendo autoridad para credenciales y
acceso a AWS. Errores o timeouts conservan el fallback seguro, sin interrumpir
el canal ni revelar detalles internos.

Para resultados de catálogo con varias variantes, el humanizador debe conservar
el orden determinístico del resultado PostgreSQL. WCS valida también ese orden
de SKU; si el modelo lo cambia u omite un hecho, se entrega el formatter
determinístico. La memoria de trabajo usa ese mismo orden para interpretar
referencias posicionales como “el segundo”.

El proveedor mock continúa siendo el default de los tests y no simula costos de
Bedrock. La selección productiva se realiza mediante `wcs.ai.provider=bedrock`
y el acceso se autoriza con el role IAM del runtime.

Para probar localmente los perfiles SQL con Bedrock real, sin consultar
AppConfig/Secrets Manager ni modificar AWS, usar:

```bash
mvn -B -DskipTests package
./scripts/run-bedrock-agent-runtime-local.sh
```

El script reutiliza el contenedor PostgreSQL local, habilita explícitamente
`wcs.agent-runtime.activation-enabled=true`, usa `prod` como ambiente de las
activaciones semilla y deja Telegram en modo mock para que el webhook local no
envíe mensajes externos. Las credenciales de AWS se resuelven por la cadena
estándar del SDK y nunca se escriben en el repositorio. El endpoint queda en
`http://localhost:18080`.

## Registro de prompts

Cada prompt debe tener:

- `prompt_id` y versión;
- objetivo y casos de uso;
- variables de entrada permitidas;
- política de información desconocida;
- formato de salida;
- modelo compatible;
- fixture y resultado esperado;
- costo y latencia observados;
- fecha de aprobación y owner.

## Guardrails mínimos

- No inventar disponibilidad, precios, pedidos, entregas, reembolsos ni políticas.
- No ejecutar operaciones sensibles sin una herramienta y autorización explícita.
- No enviar al modelo firmas, tokens, payloads completos de Meta ni datos innecesarios.
- Limitar contexto, tamaño de respuesta y tiempo de ejecución.
- Si falta información, usar fallback o handoff.
- No tratar una coincidencia de retrieval como verdad si la fuente está vencida, fuera de scope o debajo del umbral definido.
- Separar el contenido recuperado de instrucciones del usuario y proteger el prompt contra prompt injection.

## Evaluación

El piloto requiere un dataset sanitizado con casos frecuentes, ambiguos, desconocidos, adversariales y de escalamiento. Métricas mínimas: respuesta válida, groundedness según fuente autorizada, derivación correcta, latencia, costo por interacción y tasa de reintento.

## Orquestación y contrato de intención

El router responde sólo este contrato, sin SQL ni datos de negocio:

```json
{
  "intent": "CATALOG_SEARCH",
  "action": "CATALOG_SEARCH",
  "confidence": 0.94,
  "quantity": 1,
  "missingParameters": [],
  "catalogQuery": {
    "name": "remera",
    "sku": null,
    "size": "M",
    "color": "negro"
  },
  "policyKey": null
}
```

`action` es una operación allow-listed que el backend puede ejecutar; no es un
nombre de tool ni una instrucción ejecutable. Para acciones de carrito se
aceptan `ADD_TO_CART`, `VIEW_CART`, `REMOVE_FROM_CART`, `CLEAR_CART`,
`REVIEW_CHECKOUT`, `CONFIRM_CHECKOUT` y `CANCEL_CHECKOUT`. `REVIEW_CHECKOUT`
solamente presenta el resumen vigente y solicita una confirmación; no crea
pedidos ni links. El router puede indicar
`missingParameters` para pedir una aclaración antes de ejecutar. `quantity` se
normaliza a un rango acotado por el backend.

El backend valida la intención, acción, confianza mínima (`0.65`), filtros,
identidad de la conversación, ownership, stock, precios e idempotencia; luego
delega en el caso de uso existente. Una respuesta malformada o una intención
con baja confianza nunca habilita una búsqueda sin filtros ni una operación
sensible. El modelo no puede generar SQL, seleccionar un repositorio
arbitrario ni ejecutar herramientas por su cuenta.

El prompt de routing está versionado como `conversation-intent-v4` y el
texto del cliente se envía como datos delimitados y acotados. La generación de
respuestas conserva el modelo general seleccionado por `wcs.ai.model`. El
router semántico usa `conversation-router-v3` con
`openai.gpt-oss-20b-1:0`, el modelo ya autorizado y usado por WCS. El esfuerzo
de razonamiento `medium` se enviará como parámetro específico de GPT-OSS solamente
en las llamadas del router; no altera las llamadas de generación de respuestas
ni de otros agentes. El model ID, pricing y nivel de esfuerzo están definidos
en el `application.properties` versionado, sin claves adicionales de router en
AppConfig. `AI_USAGE_RECORDED` registra `reasoningEffort` para que la evaluación
compare latencia, tokens, costo y calidad bajo esa configuración.

El nivel `medium` es la configuración candidata para equilibrar razonamiento,
latencia y costo frente al `high` anterior. No se asume que mejore la calidad:
debe confirmarse con la matriz de evaluación usando el mismo dataset.

El cambio es reversible mediante una nueva versión de código o, cuando el
router se integre al Agent Registry, mediante una activación hacia otra
versión del agente. Los eventos `AI_USAGE_RECORDED` incluyen `agentId`,
`agentVersion`, `model`, `pricingVersion`, tokens, costo y latencia para
comparar las versiones sin registrar prompts ni conversaciones.

La implementación usa client-side tool use cuando está habilitado. El backend
continúa validando el resultado y ejecutando únicamente la operación
allow-listed; Luna no obtiene autoridad para ejecutar SQL, pagar o modificar
el carrito. Bedrock documenta Converse y client-side tool use para este modelo,
pero no structured outputs ni server-side tool use en `bedrock-runtime`, por
eso el contrato JSON/tool de WCS sigue siendo validado en la aplicación.
GPT-OSS puede emitir un bloque de razonamiento antes del resultado final.
`maxTokens` incluye razonamiento y respuesta. El router tendrá un límite
versionado de `2048` tokens para reducir truncamientos del JSON de decisión;
la generación de respuestas conserva su límite independiente de `1024`.
Ambos están por debajo del máximo de salida de 16K documentado por AWS para
GPT-OSS 20B. El adapter sólo extrae bloques de texto finales, nunca
razonamiento ni prompts.
El contrato exige una confianza numérica. Si Bedrock devuelve una intención
`GENERAL_SUPPORT` válida pero omite la confianza, el backend aplica `0.70` sólo
para ese camino documental de bajo riesgo; las intenciones operativas siguen
siendo rechazadas cuando la confianza está ausente o malformada.

### Router conversacional estructurado — WCS-130 y WCS-131

El router usa Bedrock para interpretar lenguaje natural, continuidad y errores
de escritura y convertirlos en una decisión estructurada. La ejecución sigue
siendo responsabilidad del backend:

```text
mensaje + contexto acotado
        ↓
Bedrock: intent + action + filtros + cantidad + faltantes
        ↓ validación allow-list / confianza / ownership
caso de uso WCS existente
        ↓
PostgreSQL, Knowledge Base, carrito, pagos u handoff
```

La versión `v4` mantiene compatibilidad con respuestas de prompts anteriores:
si falta `action`, se deriva de `intent`; si el JSON no es válido o la
confianza es insuficiente, se usa el fallback seguro. Los comandos
determinísticos de carrito continúan teniendo prioridad y no dependen del LLM.
La activación del router natural requiere el proveedor Bedrock y la versión de
prompt correspondiente; el proveedor `mock` permanece destinado a tests y
desarrollo.

WCS-131 agrega ejemplos few-shot contrastivos y un dataset sintético versionado
en `src/test/resources/fixtures/conversation-intent-v4.json`. El objetivo no es
convertir el prompt en una fuente de verdad, sino reducir confusiones entre
interés de catálogo y operaciones de compra. Por ejemplo, `quiero un buzo` es
`CATALOG_SEARCH`, mientras que `quiero comprar el buzo negro talle XL` es una
acción de checkout. El dataset se valida sin llamar a AWS y no contiene
conversaciones reales, teléfonos ni otros datos personales.

La interpretación semántica de Bedrock también debe distinguir filtros
conversacionales de nombres de productos. Expresiones como `soy talle M` sólo
producen `size=M`; palabras genéricas como `ropa` no se convierten en
`catalogQuery.name`. Una pregunta general como `tenes ropa` limpia la selección
anterior y solicita el catálogo completo. El parser determinista sólo normaliza
y valida esta decisión, evitando que residuos del lenguaje (`soy`, `tengo`,
`ropa`) creen filtros que luego produzcan falsos `NO_MATCH`.

Como segunda barrera, el orquestador puede rescatar de forma determinística una
consulta estructurada de catálogo si Bedrock la clasifica erróneamente como
`PURCHASE_LINK`, siempre que el mensaje no contenga un marcador explícito de
compra. Una solicitud explícita de comprar, pagar, confirmar o pedir un link
conserva la ruta operacional y sus validaciones de stock, ownership,
idempotencia y pago.

### Perfil de configuración del router

```properties
wcs.ai.model=openai.gpt-oss-20b-1:0
wcs.ai.router.model=openai.gpt-oss-20b-1:0
wcs.ai.router.version=conversation-router-v3
wcs.ai.router.pricing-version=aws-bedrock-us-east-1-standard-2026-09
wcs.ai.router.input-price-usd-per-million-tokens=0.0721
wcs.ai.router.output-price-usd-per-million-tokens=0.3090
wcs.ai.router.reasoning-effort=medium
```

`wcs.ai.model` y sus precios siguen siendo la configuración de la respuesta
generada y de los agentes que todavía no tienen un perfil dedicado. No se debe
reemplazar globalmente ese valor para probar el router: eso impediría separar
la mejora de interpretación del resto del flujo.

## Datos dinámicos y tools

Los datos transaccionales no se consultan como texto vectorizado. La frontera
interna de tools de WCS es provider-neutral y no depende de MCP. Cada tool tiene
nombre, descripción, versiones de schema de input/output, capability requerida
y un input tipado; el registro rechaza duplicados al iniciar la aplicación.
El adapter de Bedrock ya puede mapear el contrato de routing a Tool Use cuando
`wcs.ai.structured-tool-calling.enabled=true`, pero la ejecución siempre
delega en un servicio de aplicación:

| Tool | Fuente | Parámetros iniciales |
| --- | --- | --- |
| `catalog.search` | PostgreSQL | `name`, `sku`, `size`, `color`, `productType`, `minPrice`, `maxPrice` |
| `get_stock` | PostgreSQL/inventario | `sku`, variante |
| `get_cart` | Servicio transaccional | `customerId` |
| `get_order_status` | Servicio de pedidos | `customerId`, `orderId` |

El LLM no recibe credenciales, no genera SQL ejecutable y no puede inventar
precio, stock, carrito ni estado de pedido. Las consultas se mantienen
parametrizadas y allow-listed. Para DynamoDB se implementará un adapter de
persistencia equivalente.

La primera implementación ejecutable es `catalog.search`, registrada en
`WcsToolRegistry` y utilizada por `CatalogSpecialistExecutor`. Las demás
capacidades quedan como contratos siguientes; no se simulan como una única
tool genérica `query_database`.

Las preguntas documentales pasan por `KnowledgeRetriever`; las preguntas
dinámicas pasan por tools de aplicación. Una pregunta mixta puede combinar
ambos caminos antes de redactar la respuesta final.

El router actual se conserva porque contiene reglas de workflow —carrito,
confirmación, handoff, pagos y fallback— además de la selección semántica. La
normalización determinística reconcilia filtros explícitos del mensaje con la
propuesta del modelo: por ejemplo, `quiero un buzo` siempre conserva
`productType=buzo` y no permite que `name=buzo` sustituya la categoría. El LLM
interpreta; PostgreSQL y los servicios de aplicación resuelven y validan.

MCP no forma parte del runtime. Si en el futuro otros clientes externos
necesitan consumir estas mismas capacidades, se agregará un adapter MCP sobre el
registro actual sin duplicar lógica, SQL, autorización ni reglas de negocio.

### Continuidad y consultas compuestas del catálogo

El contexto de catálogo se conserva como filtros estructurados y no como una verdad
generada por el modelo. Seguimientos como `qué opciones tienen`, `mostrame
alternativas` o `de lo anterior` reutilizan el último filtro activo y sólo lo
reemplazan cuando el cliente expresa un cambio de tipo, nombre, talle, color o
precio. La ventana de mensajes usada para reconstruir ese estado contiene sólo
mensajes inbound; las respuestas del bot no se vuelven a interpretar como
consultas del cliente.

Una consulta mixta acotada, como `cuánto cuesta y cómo se hace el envío`, se
resuelve en dos componentes: el precio se vuelve a consultar en PostgreSQL y
la información de envío se obtiene de la política publicada. La respuesta se
compone después de obtener ambas fuentes. Si no existe un producto activo, se
responde sólo con la política o se solicita una identificación más precisa.

Las categorías que no existen en el catálogo, por ejemplo gorras o zapatillas,
se tratan como una búsqueda de catálogo sin coincidencias. El fallback es único
y seguro: no confirma disponibilidad y sólo ofrece alternativas que realmente
fueron retornadas por PostgreSQL.

Una consulta general de catálogo, por ejemplo `¿Qué productos tienen?`, se
resuelve como `search_catalog` sin filtros y devuelve una lista acotada de
variantes reales. Las preguntas de seguimiento de bajo riesgo, como `¿Está
disponible?` o `¿Cuánto cuesta?`, reutilizan el último contexto de catálogo
cuando existe una única coincidencia; si hay varias, el bot solicita el SKU o
una identificación más precisa. La disponibilidad y el precio se vuelven a
consultar en PostgreSQL y nunca se toman de la memoria o del texto generado.

### Memoria estructurada y contexto por agente — WCS-141

La memoria durable sigue siendo tipada y pequeña: preferencias explícitas
permitidas (`preferred_color`, `preferred_size`) en la tabla PostgreSQL
existente y selección/filtros de trabajo en el contexto de conversación. No se
guarda un transcript ni un blob JSON libre. El router recibe una proyección JSON
acotada de esa memoria para mapear frases coloquiales a la decisión WCS; la
reconciliación determinística controla precedencia y el catálogo no recibe
historial completo.

| Componente | Contexto permitido |
| --- | --- |
| `conversation-router` | Mensaje actual, ventana/resumen acotados, selección activa y preferencias explícitas permitidas en JSON; sin ID externo del cliente |
| Reconciliador WCS | Decisión tipada, filtros actuales, selección y preferencias validadas; aplica preferencias sólo a búsquedas específicas incompletas |
| `catalog-specialist` | `CatalogQuery` normalizado y último turno para aclaración; no transcript completo ni memoria durable |
| `response-generation` | Mensaje, historial/resumen acotados y conocimiento aprobado; no preferencias ni selección estructurada |
| `response-humanization` | Hechos estructurados ya validados, nunca memoria ni transcript |

Talle/color expresados explícitamente pueden completar una consulta posterior,
pero nunca alteran una lista general, una instrucción de carrito/pago ni un
filtro distinto que el usuario exprese en el turno actual. “No quiero eso” no
es una orden de borrado porque el referente es ambiguo: el bot pide aclaración.
El olvido de “mi talle”/“mi color” elimina sólo esa clave. No se persisten
precio, stock, carrito, pedido, intención inferida ni hechos sensibles.

## Contrato de aplicación

```text
InboundMessage
  → ConversationOrchestrator
  → ConversationIntentClassifier
  → caso de uso interno
  → OutboundMessagePort

GENERAL_SUPPORT
  → KnowledgeRetriever
  → LlmClient

CATALOG_SEARCH / STOCK / CART / ORDER
  → contrato estructurado validado por WCS
  → caso de uso WCS
  → PostgreSQL o adapter transaccional
  → LlmClient para redactar sólo con el resultado validado
```

Los tests deben poder ejecutar el mismo flujo con `MockKnowledgeRetriever`,
`MockLlmClient` y `MockWhatsAppAdapter`, sin convertir esos dobles en el modo
normal de ejecución.

## Primer límite de ejecución especialista

`catalog-specialist` es la primera implementación del contrato de agente
ejecutable. Su definición publicada puede autorizar la capacidad
`catalog.search`, pero el ejecutor sólo acepta filtros del tipo `CatalogQuery`
y delega la consulta en el servicio de catálogo existente. No acepta SQL,
prompts ni argumentos arbitrarios. La salida sigue siendo texto construido a
partir de resultados PostgreSQL determinísticos.

La tool ejecutable de catálogo se habilita únicamente cuando la definición
runtime está activa. Ante una definición ausente, un tool no permitido, un
resultado vacío o una excepción, se conserva el flujo legacy y se emite un
evento de fallback. El Tool Use estructurado de Bedrock sólo se aplica al
contrato `conversation.route` cuando el flag explícito está activo; no entrega
al modelo autoridad para ejecutar operaciones ni requiere migrar el catálogo a
un mega-tool.

El resultado de catálogo no es texto libre: `CatalogSearchResult` define el
estado (`MATCHED`, `NO_MATCH`, `CLARIFICATION`, `AMBIGUOUS` o `ALTERNATIVES`)
y una lista limitada de `CatalogFact`. `CatalogResponseFormatter` convierte
esos hechos en el texto actual del canal. Un futuro `response-humanizer` podrá
adaptar tono, idioma y formato, pero no podrá agregar hechos que no estén en
el resultado validado.

## Humanización Bedrock del catálogo (WCS-133)

Cuando `wcs.ai.provider=bedrock`, `BedrockResponseHumanizer` implementa el
contrato `ResponseHumanizer` y se usa también en la ruta normal de catálogo.
Recibe únicamente el rendering acotado de `CatalogSearchResult`, junto con el
caso de uso y el canal. No recibe el mensaje completo, SQL, credenciales ni
acceso a PostgreSQL. El prompt se resuelve mediante `PromptRegistry` y se
identifica en observabilidad por versión y hash, sin registrar su contenido.

La respuesta de Bedrock se acepta sólo si conserva los identificadores y
afirmaciones estructuradas relevantes —producto, SKU, talle, color, moneda,
precio y stock cuando corresponda— y no incorpora SKU, importes o cantidades
desconocidos. Ante error, timeout, respuesta vacía o hechos no preservados, se
devuelve `CatalogResponseFormatter` y se conserva la imagen del resultado.

Con `wcs.ai.provider=mock` o sin la propiedad, sólo se registra
`DeterministicResponseHumanizer`. Así existe un único bean productivo por
proveedor y el modo de tests no depende de AWS. Los eventos
`RESPONSE_POLICY_APPLIED` y `RESPONSE_POLICY_FALLBACK` permiten comparar
aplicaciones y fallbacks. El fallback agrega diagnóstico sanitizado de campos
faltantes y categorías de claims no aprobados, pero nunca registra el texto
generado ni sus valores de negocio. `AI_USAGE_RECORDED` conserva tokens,
latencia, costo, modelo, versión y hash del prompt sin texto conversacional.
