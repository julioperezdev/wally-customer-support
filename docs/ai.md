# IA, LLM y RAG — WCS

Owner: AI/Tech Lead  
Status: `Accepted`
Last reviewed: 2026-09-03
Related Jira: `WCS-11`, `WCS-20`, `WCS-21`, `WCS-30`
Related repository paths: `src/main/java/.../application/ai`, `src/main/resources/prompts`, `src/test/resources/fixtures`

## Registro de modelos

| ID lógico | Proveedor | Uso | Estado |
| --- | --- | --- | --- |
| `llm.mock.v1` | Interno | Desarrollo, tests y fixtures | Accepted |
| `llm.bedrock.openai.gpt-oss-20b.v1` | AWS Bedrock | Clasificación de intención y soporte general | Accepted |
| `llm.bedrock.nova-pro.v1` | AWS Bedrock | Referencia histórica para generación documental | Reference |

Bedrock se integra detrás de `ConversationIntentClassifier` y `LlmClient`.
`ConversationOrchestrator` sólo consume decisiones estructuradas del clasificador
y ejecuta casos de uso internos. El caso de uso no conoce el model ID ni el SDK.
La selección del modelo, región, límites, timeout, guardrails y fallback se
resuelve mediante AppConfig; el acceso a Bedrock se autoriza con IAM.

Cada llamada a Bedrock Converse emite el evento estructurado
`AI_USAGE_RECORDED`, con etapa, operación, proveedor, model ID, éxito, tokens
de entrada/salida/total, latencia total, latencia reportada por el proveedor y
un costo USD estimado. El costo usa `wcs.ai.pricing-version` y los precios por
millón de tokens de entrada/salida definidos en AppConfig o en los defaults de
bootstrap. Nunca se registran prompts, respuestas ni secretos. El evento y sus
consultas están documentados en [`docs/observability.md`](observability.md) y
[`observability/grafana/queries/cloudwatch-logs-insights.md`](../observability/grafana/queries/cloudwatch-logs-insights.md).

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

Cada modelo real debe registrar proveedor, model ID, versión, límites, timeout, precio vigente, fecha de revisión y casos permitidos.

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

El clasificador responde sólo este contrato, sin SQL ni datos de negocio:

```json
{
  "intent": "CATALOG_SEARCH",
  "confidence": 0.94,
  "catalogQuery": {
    "name": "remera",
    "sku": null,
    "size": "M",
    "color": "negro"
  },
  "policyKey": null
}
```

El backend valida la intención, limita la confianza mínima a `0.65`, normaliza
los filtros y ejecuta el caso de uso. Una respuesta malformada o una intención
con baja confianza nunca habilita una búsqueda sin filtros ni una operación
sensible.

El prompt de clasificación está versionado como `conversation-intent-v1` y el
texto del cliente se envía como datos delimitados y acotados. El modelo real es
`openai.gpt-oss-20b-1:0`, seleccionado por `wcs.ai.model`.
GPT-OSS puede emitir un bloque de razonamiento antes del resultado final; por
eso la clasificación y la redacción usan un presupuesto de salida de `1024`
tokens. `maxTokens` incluye razonamiento y respuesta, y el adapter sólo extrae
bloques de texto finales, nunca razonamiento ni prompts.

## Datos dinámicos y tools

Los datos transaccionales no se consultan como texto vectorizado. Bedrock puede
proponer una tool mediante Converse, pero el backend de WCS valida sus
argumentos y ejecuta el caso de uso correspondiente:

| Tool | Fuente | Parámetros iniciales |
| --- | --- | --- |
| `search_catalog` | PostgreSQL | `name`, `sku`, `size`, `color`, `maxPrice` |
| `get_stock` | PostgreSQL/inventario | `sku`, variante |
| `get_cart` | Servicio transaccional | `customerId` |
| `get_order_status` | Servicio de pedidos | `customerId`, `orderId` |

El LLM no recibe credenciales, no genera SQL ejecutable y no puede inventar
precio, stock, carrito ni estado de pedido. Las consultas se mantienen
parametrizadas y allow-listed. Para DynamoDB se implementará un adapter de
persistencia equivalente.

Las preguntas documentales pasan por `KnowledgeRetriever`; las preguntas
dinámicas pasan por tools de aplicación. Una pregunta mixta puede combinar
ambos caminos antes de redactar la respuesta final.

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
  → Bedrock Converse tool use
  → caso de uso WCS
  → PostgreSQL o adapter transaccional
  → LlmClient para redactar sólo con el resultado validado
```

Los tests deben poder ejecutar el mismo flujo con `MockKnowledgeRetriever`,
`MockLlmClient` y `MockWhatsAppAdapter`, sin convertir esos dobles en el modo
normal de ejecución.
