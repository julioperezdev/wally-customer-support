# IA, LLM y RAG — WCS

Owner: AI/Tech Lead  
Status: `Proposed`  
Last reviewed: 2026-08-30  
Related Jira: `WCS-11`, `WCS-20`, `WCS-21`  
Related repository paths: `src/main/java/.../application/ai`, `src/main/resources/prompts`, `src/test/resources/fixtures`

## Registro de modelos

| ID lógico | Proveedor | Uso | Estado |
| --- | --- | --- | --- |
| `llm.mock.v1` | Interno | Desarrollo, tests y fixtures | Accepted |
| `llm.bedrock.TBD.v1` | AWS Bedrock | Respuestas reales de soporte | Proposed |

Bedrock se integra detrás de `LlmClient`. El caso de uso no conoce el model ID ni el SDK. La selección del modelo, región, límites, timeout, guardrails y fallback se resuelve mediante AppConfig; el acceso se autoriza con IAM.

## RAG

RAG se integra detrás de `KnowledgeRetriever` y se mantiene separado de `LlmClient`:

| ID lógico | Adapter | Uso | Estado |
| --- | --- | --- | --- |
| `knowledge.mock.v1` | Interno | Tests y desarrollo local | Accepted |
| `knowledge.bedrock-kb.v1` | AWS Bedrock Knowledge Bases | Fuente administrada por AWS | Proposed |
| `knowledge.pgvector.v1` | PostgreSQL + pgvector | Índice propio, control de chunks y filtros | Proposed |

La elección entre Knowledge Bases y pgvector no bloquea la aplicación. Ambas implementaciones entregan el mismo `RetrievedContext`, con source ID, versión, score y fragmentos limitados. La ingestión, versionado y borrado de documentos se diseña como un flujo separado de la consulta.

No se fija todavía la dimensión del vector ni el modelo de embeddings: hacerlo antes de seleccionar el proveedor produciría una migración difícil de revertir.

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

## Contrato de aplicación

```text
InboundMessage
  → ConversationContextBuilder
  → KnowledgeRetriever (opcional según política)
  → LlmClient
  → ResponsePolicy
  → OutboundMessagePort
```

El modo `local-mock` debe poder ejecutar el mismo flujo con `MockKnowledgeRetriever`, `MockLlmClient` y `MockWhatsAppAdapter`.
