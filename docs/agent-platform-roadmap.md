# WCS — Plataforma de agentes conversacionales

- Owner: Product/Tech Lead
- Status: `Accepted`
- Last reviewed: 2026-09-07
- Related Jira: `WCS-45`
- Baseline: [`wcs-baseline-2026-09-07`](baselines/wcs-baseline-2026-09-07.md)
- Canonical Confluence: [WCS — Agent Platform Roadmap & Architecture Proposal](https://julioperezdev.atlassian.net/wiki/spaces/SD/pages/7569410/WCS+Agent+Platform+Roadmap+Architecture+Proposal)
- Related repository paths: `docs/roadmap.md`, `docs/ai.md`, `docs/observability.md`, `docs/decisions/`

## Propósito

Definir la evolución de WCS desde un chatbot con casos de uso y adapters hacia
una plataforma trazable para crear, versionar, evaluar y operar agentes de IA.

Esta fase es de análisis y diseño. No autoriza todavía cambios del runtime,
creación de recursos AWS, exposición de PostgreSQL ni adopción de un framework
de agentes. La implementación comenzará sólo cuando esta propuesta y sus
decisiones pendientes estén aceptadas.

## Objetivos

- interpretar una conversación y seleccionar el caso de uso correcto;
- permitir agentes especialistas con responsabilidades pequeñas y explícitas;
- mantener los datos de negocio determinísticos y verificables;
- versionar prompts, modelos, tools, políticas y parámetros de inferencia;
- comparar agentes y versiones con datasets reproducibles;
- activar o revertir versiones mediante feature flags auditables;
- medir calidad, grounding, latencia, tokens, errores y costo estimado;
- administrar configuraciones desde un backoffice React/TypeScript;
- conservar la compatibilidad con el runtime actual y un rollback simple al
  baseline protegido.

## No objetivos iniciales

- construir una colonia de agentes autónomos sin límites de presupuesto o
  cantidad de pasos;
- permitir que un LLM genere y ejecute SQL en producción;
- usar la memoria, una Knowledge Base o el texto generado como autoridad para
  stock, precio, carrito o pedidos;
- reemplazar los casos de uso de WCS por prompts sin contratos ni validaciones;
- incorporar LangChain o LangGraph como dependencias del backend;
- usar MCP como acceso directo e irrestricto a la base productiva;
- crear el backoffice antes de aceptar el modelo de dominio y los contratos;
- modificar el runtime actual durante esta etapa de definición.

## Decisión arquitectónica propuesta

WCS tendrá un orquestador acotado y agentes especialistas detrás de contratos
propios. El orquestador decide qué flujo ejecutar; el agente especialista
interpreta y compone una decisión dentro de su responsabilidad; las tools WCS
validan y ejecutan las operaciones reales; una política final controla la
respuesta antes de enviarla al canal.

No se propone que todos los agentes conversen libremente entre sí. Cada flujo
debe tener un grafo pequeño, explícito y con límites de pasos, tiempo, tokens y
costo.

```text
WhatsApp / Telegram / canal futuro
                |
                v
InboundMessageApplicationService
                |
                v
ConversationOrchestrator
  |             |              |
  |             |              +--> ConversationMemory / contexto acotado
  |             +-----------------> Agent Registry + Feature Flags
  +-------------------------------> Routing Policy
                |
                v
        Execution Plan validado
                |
       +--------+---------+----------------+
       v                  v                v
  Catalog Agent      Knowledge Agent   Support/Safety Agent
       |                  |                |
       v                  v                v
  Tools WCS          Bedrock KB       políticas/fallback
       |                  |                |
       +------------------+----------------+
                          v
                 Fact/Result Validator
                          |
                          v
                   Response Humanizer
                          |
                          v
                    Outbox / canal
```

El `Response Humanizer` puede mejorar claridad, tono y formato, pero no puede
agregar hechos, modificar precios, inventar stock, cambiar una decisión de
seguridad ni ocultar un fallback. Recibe hechos y límites estructurados, no
acceso directo a la base.

## Agentes core del primer diseño

Los siguientes son roles de diseño, no implementaciones aprobadas todavía:

| Agente | Responsabilidad | Fuente autorizada | Salida |
| --- | --- | --- | --- |
| `conversation-router` | Mapear el mensaje y el contexto a un caso de uso | Contratos de intención y contexto | Plan de ejecución tipado |
| `catalog-specialist` | Buscar productos, variantes, precio y stock | Tools determinísticas sobre PostgreSQL | Resultado de catálogo verificable |
| `knowledge-specialist` | Responder horarios, ubicación, envíos y políticas | Knowledge Base de WCS | Evidencia y respuesta propuesta |
| `support-safety` | Detectar incertidumbre, operación sensible o derivación | Políticas WCS | Fallback, escalamiento o bloqueo |
| `response-humanizer` | Adaptar tono, idioma y formato al canal | Hechos validados y política de estilo | Texto final sin hechos nuevos |

El primer flujo de negocio a priorizar es el catálogo porque permite verificar
la separación entre lenguaje natural, recuperación determinística y respuesta
humana:

```text
mensaje + contexto
    -> router
    -> catalog-specialist
    -> CatalogQuery validado
    -> CatalogQueryService / PostgreSQL
    -> resultado estructurado
    -> response-humanizer
    -> respuesta por el canal
```

## Frontera de datos y herramientas

| Tipo de información | Fuente de verdad | Acceso del agente |
| --- | --- | --- |
| Producto, variante, precio y stock | PostgreSQL | Sólo mediante tools WCS tipadas |
| Carrito, pedidos y estado de compra | Servicios transaccionales | Tools con ownership y autorización |
| Horarios, ubicación, envíos y políticas | Bedrock Knowledge Base | Retriever con evidencia y vigencia |
| Estado de conversación y filtros | WCS/PostgreSQL | Contrato de memoria con límites |
| Prompts, modelos y políticas | Registry de agentes | API de control, versiones inmutables |
| Secretos y tokens | Secrets Manager | Nunca forman parte del contexto |

Reglas obligatorias:

1. El LLM no genera SQL ejecutable.
2. Las tools reciben DTOs validados y devuelven resultados estructurados.
3. Los datos transaccionales no se deducen de texto, memoria o embeddings.
4. El resultado de una tool debe conservar fuente, timestamp y estado de
   disponibilidad cuando esos datos sean relevantes.
5. El agente no puede invocar tools fuera de su allowlist.
6. Cada tool tiene timeout, límite de filas, límite de reintentos y política de
   errores.

### MCP para PostgreSQL

MCP no forma parte del runtime productivo inicial. Puede evaluarse como una
herramienta de análisis para desarrollo o backoffice con estas condiciones:

- usuario IAM/DB separado y sólo lectura;
- vistas o réplica de lectura, nunca credenciales de escritura;
- schema y tablas explícitamente allowlisted;
- consultas limitadas por tiempo, filas y costo;
- auditoría de consulta, actor, agente y resultado resumido;
- sin acceso a secretos, PII innecesaria ni payloads completos de canales;
- sin ejecutar SQL generado por el usuario o por un LLM sin validación;
- el runtime mantiene tools WCS determinísticas aunque MCP esté habilitado en
  un entorno de análisis.

## Modelo mínimo de un agente

Una versión publicada de agente debe ser inmutable y contener, como mínimo:

```text
agentId
agentVersion
name
purpose
status
modelProvider
modelId
inferenceParameters
systemPromptVersion
inputSchemaVersion
outputSchemaVersion
allowedTools
knowledgeSources
memoryPolicy
responsePolicy
timeoutMs
maxSteps
maxInputTokens
maxOutputTokens
budgetLimit
fallbackAgentId
evaluationSuiteVersion
createdBy / createdAt
approvedBy / approvedAt
```

Los prompts se identifican por versión y hash. Los modelos se identifican por
provider, model ID y versión de configuración. No se debe registrar el prompt
completo ni el contenido de conversaciones en logs operativos.

## Registry, publicación y feature flags

La primera parte del contrato se implementa en `WCS-47` y se documenta en
[`ADR-005`](decisions/005-agent-registry-contract.md). Ese contrato es sólo de
dominio: todavía no persiste versiones ni cambia la configuración activa de
los ambientes.

El registry debe separar borradores de artefactos publicados:

```text
Draft -> Candidate -> Evaluated -> Approved -> Active -> Deprecated
                                      |
                                      +--> Rolled back
```

Reglas propuestas:

- una versión publicada nunca se edita;
- la activación apunta a `agentId + agentVersion`, no a contenido mutable;
- el flag puede seleccionar ambiente, canal, tenant futuro y porcentaje de
  tráfico;
- toda activación registra actor, motivo, timestamp y versión anterior;
- existe un kill switch por agente y por flujo;
- si falla el registry, se usa la última versión aprobada conocida o el flujo
  determinístico seguro;
- AppConfig administra referencias de activación y límites operativos;
- Secrets Manager conserva sólo secretos y credenciales;
- el contenido de prompts y contratos debe tener revisión y trazabilidad. La
  decisión entre Git como fuente de promoción, registry en PostgreSQL o ambos
  queda abierta para la siguiente fase.

## Evaluación

Cada agente debe tener un conjunto de escenarios versionado, con entrada,
contexto permitido, tools esperadas, hechos esperados y criterios de respuesta.

Métricas mínimas:

- exactitud de intención y selección de tool;
- validez de argumentos de la tool;
- grounding y ausencia de hechos inventados;
- cumplimiento de políticas y fallback seguro;
- calidad de respuesta y adecuación al canal;
- latencia total y latencia por etapa;
- tokens de entrada/salida y costo estimado;
- tasa de error, timeout, reintento y escalamiento;
- regresiones frente a la versión activa.

La comparación debe poder responder: “¿qué versión de agente, con qué modelo y
qué prompt, funciona mejor para este caso de uso bajo este costo y latencia?”.
No se promociona una versión sólo porque produzca texto más natural.

## Observabilidad

Cada ejecución debe correlacionar, sin guardar PII innecesaria:

```text
requestId
conversationId pseudonimizado
channel
useCase
workflowVersion
agentId / agentVersion
modelProvider / modelId
toolName / toolVersion
outcome
latencyMs
inputTokens / outputTokens / totalTokens
estimatedCost
fallbackReason
featureFlag
```

Eventos mínimos:

- `AGENT_ROUTED`;
- `AGENT_EXECUTION_STARTED`;
- `AGENT_EXECUTION_COMPLETED`;
- `TOOL_EXECUTION_COMPLETED`;
- `AI_USAGE_RECORDED`;
- `RESPONSE_POLICY_APPLIED`;
- `CONVERSATION_FALLBACK`.

Los dashboards deben agrupar por agente, versión, modelo, caso de uso, canal y
resultado. Las consultas no deben depender de texto libre ni de conversaciones
completas.

## Backoffice propuesto

El backoffice React/TypeScript administrará el control plane, no ejecutará
consultas de negocio en nombre del usuario.

Capacidades por etapas:

1. listar agentes y versiones;
2. crear un borrador con prompt, modelo, parámetros, tools y políticas;
3. validar contratos y dependencias;
4. ejecutar evaluación sobre un dataset seleccionado;
5. comparar versiones y costo/latencia;
6. solicitar/aprobar publicación;
7. activar, canary, pausar o revertir mediante feature flags;
8. consultar ejecuciones y métricas agregadas;
9. registrar auditoría y permisos.

El backoffice no mostrará tokens, secretos ni conversaciones completas por
defecto. El acceso a muestras de debugging debe estar protegido, minimizado y
con retención definida.

## Fases propuestas

### Fase A — Contrato y arquitectura

Aceptar esta propuesta, el ADR asociado, el modelo mínimo de agente, la
frontera de datos y los gates de seguridad. No hay código ni infraestructura.

### Fase B — Runtime del orquestador

Introducir un `ConversationOrchestrator` con plan de ejecución tipado, límites
de pasos y adapters compatibles con los casos de uso actuales. El fallback
determinístico debe seguir funcionando.

### Fase C — Registry y versionado

Definir el modelo de control plane, estados de publicación, hashes, permisos,
AppConfig para punteros activos y rollback. No activar edición mutable de
prompts en producción.

### Fase D — Agentes core y tools

Migrar primero catálogo, conocimiento estático, soporte/seguridad y
humanización. Cada agente debe conservar el límite entre datos estructurados y
texto.

### Fase E — Evaluación y observabilidad

Crear datasets, runner de evaluación, eventos estructurados, dashboards y
presupuesto por agente. La promoción requiere evidencia comparable.

### Fase F — Backoffice

Construir el panel React/TypeScript con permisos, drafts, evaluación,
publicación, feature flags, canary y rollback.

### Fase G — Migración controlada

Ejecutar shadow/canary por canal y caso de uso. Comparar contra el runtime
actual, mantener fallback y avanzar sólo con gates funcionales, operativos y de
privacidad aprobados.

### Fase H — Evaluaciones opcionales

Evaluar MCP read-only, AgentCore Memory u otros servicios sólo si aportan una
mejora demostrable sin convertirlos en autoridad transaccional ni dependencia
obligatoria del dominio.

## Rollback

Hay tres niveles de rollback:

1. **Configuración:** volver el feature flag a la versión aprobada anterior.
2. **Runtime:** desactivar el orquestador y usar el flujo actual detrás del
   adapter existente.
3. **Repositorio/infraestructura:** restaurar el tag
   `wcs-baseline-2026-09-07` y la branch
   `backup/wcs-baseline-2026-09-07` según el procedimiento del baseline.

Ninguna fase puede eliminar migraciones aplicadas ni destruir recursos para
volver atrás.

## Gates antes de implementar

- aceptar el ADR y esta propuesta en Confluence;
- definir si el registry publicado vive en Git, PostgreSQL o en un modelo
  híbrido;
- definir permisos del backoffice y separación de ambientes;
- aceptar el dataset mínimo de evaluación;
- aceptar presupuesto de tokens, latencia y costo por caso de uso;
- confirmar la política de retención de trazas y muestras de debugging;
- definir si `response-humanizer` es un agente independiente o una política de
  presentación con LLM;
- confirmar que MCP queda limitado a desarrollo/admin read-only;
- crear las issues hijas de implementación bajo `WCS-45`.

## Decisiones abiertas

1. ¿Git, PostgreSQL o ambos serán la fuente de promoción de prompts y agentes?
2. ¿Qué modelo se asignará inicialmente a router, catálogo, conocimiento y
   humanizador?
3. ¿El backoffice podrá publicar directamente o requerirá aprobación por PR?
4. ¿Qué porcentaje de tráfico se usará para canary y durante cuánto tiempo?
5. ¿Qué datos mínimos podrá ver un operador al investigar una ejecución?
6. ¿Qué umbral de calidad bloquea una promoción?
7. ¿AgentCore Memory se evaluará después de estabilizar PostgreSQL o en paralelo?

## Estado

Esta propuesta convierte la idea de una plataforma multiagente en un programa
trazable. Hasta que `WCS-45` sea aceptado, el runtime actual, sus contratos,
Knowledge Base, memoria PostgreSQL y fallback siguen siendo la referencia
operativa.
