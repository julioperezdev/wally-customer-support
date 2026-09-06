# ADR-003 — Frontera de memoria conversacional y AgentCore

Owner: Tech Lead
Status: `Accepted for phased implementation`
Date: 2026-09-05
Related Jira: `WCS-20`, `WCS-21`, `WCS-30`, `WCS-33`
Related decision: `docs/decisions/002-static-knowledge-and-dynamic-data.md`

## Contexto

WCS necesita resolver consultas de varios turnos y, eventualmente, conservar
resúmenes o preferencias. La documentación de AWS distingue memoria de corto
plazo por sesión de memoria de largo plazo para resúmenes, hechos y
preferencias. Esto es distinto de la Knowledge Base, que contiene conocimiento
documental de la tienda.

## Decisión

WCS mantendrá una abstracción propia `ConversationMemory` y usará PostgreSQL
como primera implementación. AgentCore Memory se evaluará posteriormente detrás
de un adapter, con datos sintéticos y sin modificar el dominio.

LangChain y LangGraph no se incorporarán como dependencias. Sus conceptos de
estado, `thread_id`, checkpoint y store se mapearán a contratos Java de WCS.

## Consecuencias

- El estado de filtros y la memoria de sesión quedan bajo ownership de WCS.
- PostgreSQL continúa siendo la fuente de verdad de datos transaccionales.
- AgentCore no puede determinar stock, precio, pedidos o carrito.
- Se puede comparar PostgreSQL y AgentCore sin reescribir los casos de uso.
- La retención, borrado, aislamiento y clasificación de datos deben aprobarse
  antes de memoria de largo plazo o producción.
- El costo de AgentCore se mantiene opcional y proporcional al uso.

## Reglas de implementación

1. `conversationId` interno es la unidad de ownership.
2. `actorId` debe ser interno o pseudónimo; no se usa el teléfono como memoria.
3. El estado de búsqueda actual no se trata como preferencia persistente.
4. Los mensajes enviados al LLM tienen límites de tamaño y no se registran en
   logs.
5. Las migraciones aplicadas no se modifican; todo cambio usa una nueva
   migración y evidencia de compatibilidad.
6. Si AgentCore falla, WCS debe conservar un fallback seguro.

## Alternativas descartadas por ahora

- Adoptar AgentCore como dependencia obligatoria del MVP.
- Usar una Knowledge Base para stock, precios o pedidos.
- Vectorizar datos transaccionales como fuente principal.
- Reescribir WCS en Python con LangChain/LangGraph.
- Guardar conversaciones reales en el spike.

## Referencias

- <https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/harness-memory.html>
- <https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/memory-types.html>
- <https://docs.aws.amazon.com/bedrock/latest/userguide/knowledge-base.html>
