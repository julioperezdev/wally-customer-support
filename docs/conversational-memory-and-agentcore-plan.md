# WCS — Memoria conversacional, contexto y AgentCore

Owner: Product/Tech Lead
Status: `Accepted for phased implementation`
Last reviewed: 2026-09-05
Related Jira: `WCS-20`, `WCS-21`, `WCS-30`, `WCS-33`, `WCS-34`, `WCS-35`, `WCS-36`, `WCS-37`
Canonical Confluence: [WCS — Conversational Memory, Context & AgentCore Plan](https://julioperezdev.atlassian.net/wiki/spaces/SD/pages/6684674/WCS+Conversational+Memory+Context+AgentCore+Plan)
Related repository paths: `src/main/java/com/wally/customersupport/conversation`, `src/main/java/com/wally/customersupport/catalog`, `src/main/resources/db/migration`, `docs/ai.md`, `docs/architecture.md`
Decision/source: plan aprobado para WCS el 2026-09-05

## Propósito

Definir cómo WCS mantendrá contexto conversacional, memoria persistente, conocimiento documental y datos transaccionales sin acoplar el dominio a LangChain, LangGraph ni Amazon Bedrock AgentCore.

La implementación continuará siendo Java/Spring Boot, monolito modular hexagonal, PostgreSQL, Bedrock y adapters de WhatsApp/Telegram. LangChain y LangGraph se utilizan únicamente como referencia conceptual de patrones de estado, checkpoint y memoria.

## Decisión ejecutiva

1. WCS es dueño del estado conversacional y de las reglas de negocio.
2. PostgreSQL es la implementación inicial de memoria de sesión y estado tipado.
3. PostgreSQL continúa siendo la fuente de verdad para catálogo, variantes, precio, stock, carrito y pedidos.
4. Bedrock interpreta lenguaje natural y puede proponer una decisión o una tool, pero nunca genera SQL ni hechos de negocio.
5. Bedrock Knowledge Bases se utiliza para documentación estática de la tienda.
6. AgentCore Memory se evaluará en un spike aislado y sólo podrá incorporarse detrás de un adapter.
7. No se incorporarán LangChain ni LangGraph como dependencias del backend.

## Estado actual y brechas

WCS ya persiste conversaciones y mensajes, carga una cantidad acotada de mensajes recientes y utiliza `ConversationContext`. Sin embargo:

- el clasificador recibe principalmente el mensaje actual;
- los turnos anteriores no se utilizan de forma suficiente para resolver la intención;
- `CatalogQuery` no representa `productType` o categoría;
- el estado de filtros activos no está tipado ni tiene reglas de merge;
- no existe una separación explícita entre filtro de búsqueda actual y preferencia persistente;
- no existe todavía un contrato de memoria intercambiable;
- no existe resumen de conversaciones extensas.

El caso de aceptación inicial es:

```text
Usuario: tenés buzo nullpointer?
Usuario: pero quiero buzo
Usuario: que sea nullpointer
```

El sistema debe conservar simultáneamente `productType=BUZO` y `name=NULLPOINTER`. Si no existe esa combinación, debe informar que no hay coincidencias y ofrecer alternativas reales del catálogo.

## Arquitectura objetivo

```text
WhatsApp / Telegram / canal futuro
                ↓
InboundMessageApplicationService
                ↓
ConversationOrchestrator
                ├── ConversationMemory
                │     ├── mensajes recientes
                │     ├── estado de filtros activos
                │     ├── último intent
                │     └── resumen opcional
                ├── ConversationIntentClassifier
                │     └── Bedrock o mock
                ├── KnowledgeRetriever
                │     └── Knowledge Base estática de WCS
                ├── casos de uso y tools WCS
                │     └── PostgreSQL / servicios transaccionales
                └── ResponsePolicy
                        ↓
                 Outbox / canal de salida
```

Contrato de memoria:

```java
interface ConversationMemory {
    ConversationState load(ConversationId conversationId);
    ConversationState save(ConversationState state);
    void clear(ConversationId conversationId);
}
```

Implementaciones previstas:

```text
PostgresConversationMemory  → primera implementación
AgentCoreMemoryAdapter      → sólo después del spike
```

El dominio y los casos de uso dependen del contrato, no de PostgreSQL, AgentCore, AWS SDK o un framework de agentes.

## Modelo de identidad y sesión

- `conversationId`: identificador interno de WCS y fuente primaria de ownership.
- `sessionId`: si se integra AgentCore, se mapeará al identificador interno de conversación.
- `actorId`: identificador interno o pseudónimo estable del cliente.
- Los teléfonos, `chatId`, payloads completos y secretos no se almacenan como memoria semántica.
- El estado de una conversación no puede ser compartido entre actores.

## Frontera de datos

| Necesidad | Fuente de verdad | Política |
| --- | --- | --- |
| Filtros activos de la búsqueda | Estado WCS/PostgreSQL | Se puede actualizar o limpiar por turno |
| Conversación reciente | WCS/PostgreSQL | Contexto acotado y con retención |
| Resumen | WCS/PostgreSQL inicialmente | Se agrega sólo con evidencia de crecimiento |
| Preferencias | WCS, con confirmación/retención definida | No reemplazan filtros actuales |
| Catálogo y variantes | PostgreSQL | Consulta determinística |
| Stock y precio | PostgreSQL/inventario | Nunca se recuperan desde memoria |
| Carrito y pedidos | Servicio transaccional | Nunca se recuperan desde memoria |
| FAQ y políticas editoriales | Bedrock Knowledge Base | Sólo responder con evidencia vigente |

## Fases de implementación

### Fase 1 — Contexto conversacional y búsqueda multi-turno

**Objetivo:** interpretar una consulta usando el mensaje actual, el estado activo y una cantidad acotada de turnos anteriores.

**Alcance:**

- agregar `productType` o categoría al catálogo;
- extender `CatalogQuery` y los repositorios;
- representar el estado activo de búsqueda con filtros tipados;
- definir merge, reemplazo y limpieza de filtros;
- reconstruir el contexto desde una ventana acotada de mensajes persistidos;
- enviar contexto acotado al clasificador;
- conservar el contexto entre WhatsApp y Telegram;
- responder correctamente cuando no hay coincidencias;
- mantener el LLM fuera de SQL y de los hechos de catálogo.

**Criterios de salida:**

- “buzo nullpointer” no devuelve remeras;
- una aclaración posterior conserva las restricciones no modificadas;
- un filtro explícitamente cambiado reemplaza al anterior;
- una consulta sin coincidencias no inventa productos;
- el flujo funciona con Bedrock mock y Bedrock real;
- existen pruebas unitarias, de aplicación, integración PostgreSQL y contrato.

La persistencia independiente de `ConversationState` se implementa en la Fase
3. Esta fase no introduce memoria de largo plazo ni preferencias.

**Evidencia:** PR, `mvn verify`, pruebas de conversaciones multi-turno y logs sanitizados con intent, filtros normalizados y resultado.

### Fase 2 — Contrato de memoria, privacidad y retención

**Objetivo:** definir qué se guarda, por cuánto tiempo y quién puede acceder.

**Alcance:**

- contrato `ConversationMemory`;
- clasificación de datos;
- TTL de mensajes y estado;
- eliminación y opt-out;
- aislamiento por conversación y actor;
- límites de contexto;
- política para datos sensibles;
- decisión de qué información nunca se guarda.

**Criterio de salida:** Confluence y repositorio contienen la política aceptada; la configuración de retención es explícita; existen pruebas de aislamiento y borrado.

`WCS-34` implementa el contrato `ConversationMemory`, `ConversationState`, la
política recomendada y un adapter en memoria para tests. La persistencia real
queda en la Fase 3 y su activación productiva continúa bloqueada por el gate
legal y de negocio.

La política legal de retención sigue siendo un gate previo a producción.

### Fase 3 — Memoria de sesión en PostgreSQL

**Objetivo:** persistir estado conversacional tipado sin agregar un servicio externo.

**Alcance:**

- nueva migración Flyway, sin modificar migraciones aplicadas;
- tabla de estado por conversación en el schema `wcs`;
- repositorio JPA y adapter PostgreSQL detrás de `ConversationMemory`;
- versionado optimista o control equivalente;
- limpieza por TTL;
- recuperación después de reinicio;
- integración tolerante a fallos en el flujo inbound;
- métricas de lecturas, escrituras, conflictos y expiración;
- Testcontainers con PostgreSQL y activación explícita para integración.

**Criterio de salida:** el estado sobrevive a reinicios, no se mezcla entre
conversaciones, los conflictos se rechazan, el costo incremental se mantiene
dentro del RDS existente y producción conserva la memoria desactivada hasta la
aprobación de retención.

`WCS-35` implementa `V6__create_conversation_memory_states.sql`, la entidad,
repositorio y adapter JPA, la integración opcional del flujo inbound y las
pruebas de persistencia, ownership, versión y expiración. El flag
`wcs.conversation.memory.enabled=false` deja un adapter no-op como fallback;
los tests de integración lo cambian a `true`.

### Fase 4 — Knowledge Base estática de WCS

**Objetivo:** responder preguntas documentales con evidencia autorizada.

**Alcance:**

- documentos Markdown de Ropa de Programador;
- bucket S3, Knowledge Base, S3 Vectors y Titan Text Embeddings V2;
- metadata de idioma, versión, tipo y vigencia;
- ingestion explícita y versionada;
- adapter `KnowledgeRetriever`;
- umbral de evidencia y fallback;
- pruebas de recuperación y documentos vencidos.

**Fuera de alcance:** catálogo, stock, pedidos, carrito y generación de SQL.

Esta fase corresponde principalmente a `WCS-30`. La KB histórica `bigg-rag-sales-offhours` continúa siendo sólo referencia técnica.

`WCS-30` ya está versionado y mergeado. La validación de infraestructura,
ingesta y activación de la KB continúa siendo un gate operativo separado.

### Fase 5 — Resumen de conversaciones extensas

**Objetivo:** reducir tokens y latencia cuando el historial real lo justifique.

**Alcance:**

- métrica de tamaño de contexto;
- resumen versionado por conversación;
- punto de corte del último mensaje resumido;
- estrategia de actualización;
- fallback al historial acotado;
- evaluación de calidad del resumen.

Primero se implementará en WCS/PostgreSQL. AgentCore Summary Strategy queda como alternativa posterior.

`WCS-36` implementa esta fase con un resumen opcional, una ventana reciente
separada, checkpoint de mensajes y fallback seguro. El flag queda desactivado
por defecto hasta medir calidad, costo, latencia y retención.

### Fase 6 — Memoria semántica y preferencias

**Objetivo:** separar hechos conversacionales y preferencias duraderas de los filtros temporales.

Ejemplos:

```text
Filtro actual: “quiero un buzo negro”
Preferencia: “prefiero ropa negra”
Hecho transaccional: “el pedido 123 está enviado”
```

**Reglas:**

- stock, precio, carrito y pedidos nunca se convierten en autoridad por memoria;
- las preferencias deben tener alcance, confianza y política de eliminación;
- las preferencias no deben activar acciones sensibles;
- inicialmente se guardarán sólo preferencias explícitas o confirmadas.

Se puede evaluar la estrategia semántica y de preferencias de AgentCore, pero su extracción asíncrona no reemplaza consultas transaccionales.

`WCS-37` inicia esta fase con una implementación propia de WCS/PostgreSQL:
preferencias explícitas o confirmadas, alcance de actor/conversación, TTL,
ownership, borrado y un límite inicial a `preferred_color`. Las preferencias
se entregan como contexto auxiliar y no reemplazan filtros actuales ni datos
transaccionales. La extracción automática y AgentCore permanecen fuera del
runtime hasta completar la evaluación de calidad, costo, latencia y privacidad.

### Fase 7 — Spike de Amazon Bedrock AgentCore Memory

**Objetivo:** comparar AgentCore Memory contra la implementación PostgreSQL de WCS.

**Alcance del spike:**

- datos sintéticos;
- sesiones y actores aislados;
- memoria corta;
- resumen;
- un hecho semántico;
- una preferencia;
- recuperación;
- latencia y errores;
- IAM;
- retención y eliminación;
- costo real;
- fallback a PostgreSQL.

**Criterios para adoptar AgentCore:**

- no introduce dependencia en el dominio;
- no degrada la latencia aceptable;
- permite aislamiento y borrado verificables;
- el costo es justificable;
- no se utiliza como fuente de verdad transaccional;
- existe fallback operativo;
- la información sensible queda fuera del alcance no aprobado.

Si no supera estos criterios, WCS mantiene PostgreSQL sin rediseño.

## Costos de referencia

AgentCore Memory no tiene costo fijo ni mínimo mensual. La estimación publicada y revisada el 2026-09-05 es:

- short-term: USD 0,25 cada 1.000 eventos;
- long-term storage con estrategias integradas: USD 0,75 cada 1.000 registros/mes;
- retrieval: USD 0,50 cada 1.000 recuperaciones.

Los costos de inferencia Bedrock, Knowledge Bases, CloudWatch y cualquier estrategia personalizada se calculan por separado. Para 1.000 conversaciones mensuales de 10 interacciones, bajo los supuestos definidos en el plan, el costo base estimado de AgentCore Memory es aproximadamente USD 11,50.

Referencia: <https://aws.amazon.com/bedrock/agentcore/pricing/>

## Pruebas y evaluación

Dataset mínimo sanitizado:

- búsqueda con varios filtros;
- filtros agregados en turnos diferentes;
- corrección de un filtro;
- eliminación de un filtro;
- combinación sin coincidencias;
- ambigüedad;
- preferencia versus filtro temporal;
- aislamiento de conversaciones;
- reinicio de aplicación;
- expiración;
- error del proveedor de memoria;
- prompt injection;
- datos transaccionales siempre consultados en PostgreSQL.

Métricas:

- continuidad correcta del contexto;
- precisión de filtros;
- tasa de no-match correctamente informado;
- respuestas grounded;
- latencia;
- tokens;
- costo;
- errores;
- reintentos;
- aislamiento y eliminación.

## Dependencias y gates

1. Fase 1 puede avanzar con mocks y datos demo.
2. Fase 2 debe estar definida antes de guardar memoria de largo plazo.
3. Fase 3 precede a cualquier adopción de AgentCore en producción.
4. Fase 4 reutiliza y completa `WCS-30`.
5. Fases 5 y 6 requieren evidencia de volumen o pérdida de contexto.
6. Fase 7 no usa conversaciones reales.
7. Ninguna fase habilita tráfico productivo por sí sola.
8. Todo cambio de código requiere issue Jira `WCS-*`, branch desde `main`, PR, CI y evidencia.

## Referencias

- AWS AgentCore Memory: <https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/harness-memory.html>
- AWS Memory types: <https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/memory-types.html>
- AWS Summary Strategy: <https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/summary-strategy.html>
- AWS Semantic Memory Strategy: <https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/semantic-memory-strategy.html>
- AWS User Preference Memory Strategy: <https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/user-preference-memory-strategy.html>
- AWS Knowledge Bases: <https://docs.aws.amazon.com/bedrock/latest/userguide/knowledge-base.html>

## Registro de cambios

| Fecha | Cambio |
| --- | --- |
| 2026-09-05 | Se documentan siete fases, frontera PostgreSQL/Knowledge Base/AgentCore y exclusión de LangChain/LangGraph como dependencias. |
| 2026-09-06 | Se inicia Fase 2 con contrato de memoria, límites de contexto y política propuesta de privacidad, retención, aislamiento y borrado en `WCS-34`. |
| 2026-09-06 | `WCS-35` implementa la persistencia PostgreSQL de memoria de sesión con TTL, ownership, versión y activación controlada. |
| 2026-09-06 | `WCS-36` inicia la Fase 5 con resumen versionado, ventana reciente y fallback detrás de configuración. |
| 2026-09-06 | `WCS-37` inicia la Fase 6 con preferencias explícitas PostgreSQL, TTL, ownership, borrado y activación controlada. |
