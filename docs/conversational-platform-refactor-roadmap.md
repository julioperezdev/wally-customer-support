# WCS — Roadmap de refactor conversacional y fundación de datos

Owner: Product/Tech Lead
Status: `Proposed`
Last reviewed: 2026-09-18
Related epic: [`WCS-134`](https://julioperezdev.atlassian.net/browse/WCS-134)
Baseline de rollback: [`wcs-baseline-2026-09-07`](baselines/wcs-baseline-2026-09-07.md)
Fuente canónica detallada: [Confluence — WCS Refactor conversacional y fundación de datos](https://julioperezdev.atlassian.net/wiki/spaces/SD/pages/13533186)

## Propósito

Preparar una evolución controlada del chatbot actual para que entienda lenguaje
natural, conserve el contexto necesario y seleccione el caso de uso correcto,
sin convertir al LLM en autoridad sobre el catálogo, el stock, el carrito, los
pedidos o los pagos.

Este documento es el plan de implementación que continúa al audit de código,
datos y comportamiento realizado el 2026-09-18. La ejecución será local-first:
cada fase debe funcionar con PostgreSQL/Testcontainers, Bedrock configurable y
dobles determinísticos antes de abrir un PR o tocar AppConfig, Terraform o
App Runner.

## Problemas que resuelve

El runtime actual funciona para frases explícitas, pero presenta fragilidad en
estos escenarios:

- una categoría como “quiero un buzo” se interpreta como una búsqueda de nombre
  en vez de como filtro por tipo;
- una refinación corta como “la M”, “ese en negro” o “el anterior” pierde el
  estado de la conversación;
- una consulta de catálogo, carrito o compra puede confundirse con otra
  intención cuando el mensaje depende de turnos anteriores;
- el texto final puede ser correcto en tono, pero no siempre deja claro qué
  hechos fueron obtenidos de una fuente confiable;
- el esquema actual mezcla parte del estado conversacional con entidades de
  negocio y todavía no ofrece una frontera suficiente para evolución futura;
- existen caminos legacy y lógica de compatibilidad que deben eliminarse sólo
  después de obtener evidencia de equivalencia;
- los logs permiten observar invocaciones, pero necesitamos explicar mejor la
  decisión de routing, la tool elegida, la calidad del grounding y el motivo de
  un fallback.

## Decisiones de diseño

1. **El LLM interpreta; WCS decide y ejecuta.** El modelo puede proponer una
   intención, acción y argumentos estructurados. El backend valida la propuesta,
   resuelve ownership y ejecuta sólo una operación allow-listed.
2. **PostgreSQL es la fuente de verdad dinámica.** Catálogo, variantes, precio,
   stock, carrito, pedidos y memoria mínima no se resuelven por embeddings ni
   por texto generado.
3. **Knowledge Base es la fuente documental.** Horarios, ubicación, envíos,
   cambios, FAQ y políticas usan RAG con evidencia; no se consulta PostgreSQL
   para simular documentos ni se inventan respuestas cuando no hay evidencia.
4. **La humanización es la última etapa.** Puede mejorar claridad y tono, pero
   no puede agregar SKU, precio, stock, políticas, links de pago ni hechos no
   presentes en el resultado validado.
5. **No se incorpora MCP al runtime.** Puede evaluarse más adelante para
   desarrollo/admin read-only, con vistas allow-listed, límites y auditoría.
6. **Las migraciones son aditivas y reversibles.** No se modifica ninguna
   migración aplicada. Toda evolución se agrega mediante una nueva migración
   Flyway y se prueba sobre PostgreSQL real en Testcontainers.
7. **El cambio de proveedor es una configuración, no una reescritura.** Bedrock
   real y mocks mantienen el mismo contrato `LlmClient`/retriever/tool.

## Estado actual y punto de partida

Ya existen contratos y piezas que se deben conservar y simplificar, no
duplicar:

- router conversacional y tool calling estructurado sin MCP (`WCS-130`);
- prompt y dataset inicial de routing (`WCS-131`);
- trazabilidad y diagnóstico de decisiones (`WCS-132`);
- humanizador Bedrock condicionado (`WCS-133`);
- registry, activaciones, evaluaciones, shadow y control plane de agentes;
- carrito, checkout, Mercado Pago, Telegram/WhatsApp y Knowledge Base;
- backoffice autenticado para agentes y operación;
- observabilidad de uso de IA, routing, herramientas, fallback y latencia.

El baseline protegido sigue siendo la referencia funcional hasta completar cada
gate. La limpieza de código se realizará sólo después de demostrar que la ruta
nueva cubre el comportamiento existente y que el rollback de configuración
continúa disponible.

## Arquitectura objetivo

```text
Canal
  -> Inbound adapter
  -> conversación/ownership/deduplicación
  -> ConversationContextBuilder
  -> Intent + entity interpreter (Bedrock o mock)
  -> decisión validada y plan de ejecución
  -> caso de uso / agente especialista
       |-> tool WCS -> PostgreSQL
       |-> KnowledgeRetriever -> Bedrock Knowledge Base
       |-> adapter externo -> pagos/canal
  -> hechos y resultado validados
  -> ResponseHumanizer
  -> outbox y respuesta del canal
```

El plan de ejecución debe contener como mínimo:

```text
conversationId pseudonimizado
intent
action
confidence
entities normalizadas
missingParameters
selectedAgentId / selectedAgentVersion
allowedTool
requiresConfirmation
sourcePolicy
traceId
```

El plan nunca contiene SQL ejecutable, credenciales, tokens, prompts completos
ni payloads completos de Telegram/WhatsApp.

## Fases de implementación

### Fase 0 — Contrato, baseline y métricas de aceptación

**Jira:** `WCS-134`
**Objetivo:** congelar el alcance, establecer la métrica base y confirmar el
rollback antes de cambiar el runtime.

Entregables:

- contrato versionado `conversation-route-input-v2` y
  `conversation-execution-plan-v1`;
- matriz de casos reales: catálogo, refinamiento, stock, carrito, checkout,
  RAG, handoff, saludo y mensajes inexistentes;
- dataset de evaluación con frases formales, coloquiales, incompletas y con
  errores de escritura;
- scorecard base: intención, entidades, tool, grounding, fallback, latencia,
  costo y tasa de derivación;
- snapshot de tablas, migraciones, flags, agentes activos y dashboards;
- procedimiento de rollback probado sólo en local.

**Gate:** ningún cambio de producción. La fase termina cuando el dataset y los
umbrales están aceptados y el baseline puede reconstruirse.

### Fase 1 — Fundación de datos y contexto conversacional

**Jira:** [`WCS-135`](https://julioperezdev.atlassian.net/browse/WCS-135)
**Objetivo:** separar estado de conversación, preferencias, selección actual,
carrito y entidades de negocio para que una frase corta pueda resolverse con
contexto sin copiar conversaciones completas al prompt.

Entregables:

- revisión de `conversation`, `conversation_messages`, carrito y pedidos;
- modelo explícito de `conversation_state` o extensión equivalente para
  intención activa, filtros, entidad seleccionada, etapa del flujo y expiración;
- normalización de identidad por canal y ownership sin usar el teléfono como
  única frontera multi-tenant;
- índices para conversación activa, canal, timestamp, estado y variantes de
  catálogo;
- límites de memoria: últimos turnos, resumen sanitizado, preferencias y TTL;
- migración Flyway nueva, fixtures y tests de concurrencia/idempotencia;
- pruebas Testcontainers de consultas reales y migración limpia desde el
  schema actual.

**Gate:** las secuencias “buzo → negro → XL”, “remera → la M” y “agregá otro”
mantienen el contexto y no alteran precio/stock fuera de PostgreSQL.

### Fase 2 — Interpretación semántica y routing validado

**Jira:** [`WCS-136`](https://julioperezdev.atlassian.net/browse/WCS-136)
**Objetivo:** reemplazar heurísticas dispersas por una frontera única que
interprete el mensaje humano y produzca una decisión estructurada, sin otorgar
autoridad al LLM.

Entregables:

- `ConversationContextBuilder` con contexto acotado y ordenado;
- parser estricto de intención, acción y entidades: tipo, nombre, SKU, talle,
  color, rango de precio, cantidad y referencia anafórica;
- precedencia de datos explícitos del turno actual sobre inferencias del modelo;
- resolución de categorías contra un vocabulario de catálogo, separando “buzo”
  de “Buzo Spring Boot”;
- reconciliación de la propuesta LLM con el estado persistido y el caso de uso;
- fallback determinístico para saludo, cancelación, confirmación, baja y
  mensajes de baja confianza;
- contrato de una única tool por paso, límites de pasos, timeout, tokens y
  costo;
- logs sanitizados de decisión, entidades presentes/ausentes, corrección,
  fallback y causa de rechazo.

**Gate:** el mismo conjunto de frases obtiene la misma decisión estructural
cuando el modelo responde correctamente y un fallback seguro cuando responde
JSON inválido o ambiguo.

### Fase 3 — Agentes especialistas y herramientas de negocio

**Jira:** [`WCS-137`](https://julioperezdev.atlassian.net/browse/WCS-137)
**Objetivo:** conectar el plan validado con especialistas pequeños y tools
determinísticas, evitando un agente monolítico que conozca todo el sistema.

Agentes prioritarios:

| Agente | Responsabilidad | Fuente autorizada |
| --- | --- | --- |
| `conversation-router` | intención, acción y entidades | contexto + contrato |
| `catalog-specialist` | búsqueda, stock, precio y variantes | PostgreSQL vía tools |
| `conversation-state` | continuidad, selección y carrito | PostgreSQL vía servicio |
| `knowledge-specialist` | FAQ, horarios, ubicación, envíos y políticas | Knowledge Base |
| `checkout-specialist` | confirmación, pedido idempotente y link | carrito + pagos |
| `support-safety` | baja confianza, handoff y operaciones sensibles | políticas WCS |
| `response-humanizer` | tono, formato e imagen permitida | hechos validados |

Entregables:

- allowlist de tools por agente y versión;
- schemas de input/output versionados y validados;
- catálogo con búsqueda por campos normalizados y filtros combinables;
- RAG con citas/evidencia interna y fallback cuando la recuperación es débil;
- carrito que se reinicia junto con `/start`, respeta cantidades y exige
  confirmación antes de generar un link;
- invalidación o reemplazo controlado de links anteriores por conversación;
- flujo de handoff con contexto resumido y sin PII innecesaria;
- pruebas contractuales de que el humanizador no agrega hechos.

**Gate:** cada caso de uso puede ejecutarse con una tool determinística y el
LLM sólo selecciona/normaliza la operación permitida.

### Fase 4 — Calidad, observabilidad y evaluación continua

**Jira:** [`WCS-138`](https://julioperezdev.atlassian.net/browse/WCS-138)
**Objetivo:** poder demostrar si una versión de agente mejora la experiencia y
si el costo/latencia justifican la mejora.

Entregables:

- eventos por etapa: `ROUTING`, `AGENT`, `TOOL`, `RAG`, `RESPONSE`, `CHECKOUT`;
- correlation id, conversation hash, canal, caso de uso, agente/versión,
  modelo, tokens, costo estimado, latencia, resultado y fallback;
- evaluación offline por dataset y ejecución local con Bedrock real opcional;
- métricas separadas de exactitud de intención, extracción de entidades,
  éxito de tool, grounding, calidad, seguridad y utilidad;
- dashboards sin filas ambiguas: agregados por hora, agente, versión, modelo,
  canal, caso de uso y resultado;
- comparación baseline/candidate con el mismo dataset;
- redacción de PII y prohibición de prompts/respuestas completas en logs;
- alarmas de costo, latencia, error, fallback y regresión.

Implementación local iniciada para WCS-138:

- `AgentEvaluationQualityScorecard` separa validez de respuesta, grounding de
  hechos, seguridad y utilidad;
- `AGENT_EVALUATION_SCORECARD` expone sólo métricas agregadas y declara las
  dimensiones todavía no evaluadas;
- Grafana agrega un panel por dataset, agente, versión, proveedor y modelo;
- el scorecard no promociona agentes automáticamente ni cambia configuración
  remota.

**Gate:** no se promociona un modelo o prompt por percepción subjetiva. Debe
existir evidencia comparable y un resultado de rollback.

Implementación local iniciada para WCS-139:

- `AgentRuntimeDefinitionResolver` es la única frontera que valida el plan
  contra el especialista, sus tools y los schemas versionados;
- `ConversationOrchestrator` dejó de crear un registry paralelo;
- la activación controlada verifica el kill switch por ambiente, canal, caso de
  uso, agente y versión, y el contexto Spring prueba que existe un único
  `AgentSpecialistRegistry`;
- el inventario y las disposiciones de cleanup están en
  [`ADR-041`](decisions/041-legacy-cleanup-and-controlled-activation.md);
- los adapters mock/no-op se conservan como fallbacks explícitos y no como
  rutas productivas;
- no se modificaron migraciones, Terraform, AppConfig remoto ni despliegues.

### Fase 5 — Depuración, activación controlada y cierre

**Jira:** [`WCS-139`](https://julioperezdev.atlassian.net/browse/WCS-139)
**Objetivo:** retirar duplicaciones y compatibilidad legacy sólo cuando la ruta
objetivo tenga cobertura y esté protegida por flags.

Entregables:

- inventario final de clases, adapters, heurísticas y configuraciones sin
  referencias;
- eliminación incremental de código muerto, manteniendo un commit reversible;
- activación por ambiente/canal/caso de uso mediante AppConfig;
- shadow/canary sólo para rutas no mutantes y con kill switch;
- smoke funcional por Telegram y backoffice; WhatsApp queda independiente de
  esta fase si aún no está habilitado;
- documentación operativa, matriz de pruebas y decisión de promoción;
- PR de integración grande con migraciones, tests, dashboards y cambios de
  configuración claramente separados en commits.

**Gate:** `mvn -B verify`, tests Testcontainers, evaluación offline, smoke
local, plan Terraform sin destrucciones inesperadas, aprobación de PR y
despliegue verificable. El estado final del trabajo se marca Done sólo con
evidencia post-merge.

## Estrategia de PR y ejecución

Se crearán pocos PR, agrupados por impacto y no por clase individual:

1. **PR A:** Fases 0–1: contratos, migración de contexto, fixtures y pruebas
   de PostgreSQL.
2. **PR B:** Fases 2–3: router, entidades, tools y agentes especialistas.
3. **PR C:** Fase 4: observabilidad, evaluación y dashboards.
4. **PR D:** Fase 5: cleanup, flags, rollout y documentación final.

Cada PR debe incluir sus tests locales, evidencia, cambios de configuración y
rollback. La infraestructura remota se ejecuta después de que el código pase
en local; un cambio sólo de AppConfig puede usar el workflow de restart
documentado, pero no se debe mezclar con una migración de schema sin evidencia.

## Pruebas mínimas de aceptación

```text
/start
Busco algo para el frío
Mejor un buzo
Que sea negro
La XL
¿Cuánto cuesta?

/start
Busco una remera negra talle M de menos de 20000
¿Está disponible?
Agrega 2
Saca 1
Confirmar compra
No quiero comprar todavía

/start
¿Dónde están ubicados?
¿A qué hora abren el sábado?
¿Cómo funcionan los envíos?

/start
Necesito hablar con una persona
```

La matriz completa vive en `docs/manual-qa-runbook.md` y el dataset de
evaluación en los artefactos del contexto `agent`. Las pruebas deben validar
respuesta, intención, tool, hechos, trazabilidad y ausencia de PII, no sólo el
texto visible.

## Fuera de alcance de este roadmap

- MCP con acceso directo a la base productiva;
- SQL generado por el LLM;
- autonomía ilimitada o conversaciones entre agentes sin grafo explícito;
- multi-tenant productivo completo;
- WhatsApp productivo si la aprobación/configuración de Meta sigue pendiente;
- migrar a ECS u otro runtime por la sola observación de que App Runner ya no
  acepta nuevos clientes;
- cambiar de proveedor LLM sin dataset comparable y rollback.

## Criterio de cierre del programa

El programa se considera cerrado cuando:

1. las consultas naturales y las refinaciones principales alcanzan el umbral
   aceptado en el dataset;
2. los datos dinámicos salen de PostgreSQL y los datos documentales de RAG con
   evidencia;
3. carrito, checkout, handoff y reinicio de conversación son idempotentes;
4. cada ejecución puede explicarse por logs agregados y trazables;
5. agentes, prompts, schemas, herramientas y activaciones son versionables;
6. existe evaluación baseline/candidate y rollback probado;
7. el backoffice puede mostrar el estado sin exponer secretos o PII;
8. el despliegue post-merge y el smoke real tienen evidencia.

Hasta entonces, el runtime vigente y `wcs-baseline-2026-09-07` permanecen como
opción de retorno.
