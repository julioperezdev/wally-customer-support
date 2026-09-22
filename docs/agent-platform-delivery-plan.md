# Plan de entrega — Plataforma de agentes WCS

## Objetivo

Convertir el chat actual en una plataforma reutilizable para crear, versionar,
activar, medir y comparar agentes sin acoplar el dominio a Telegram, WhatsApp,
Bedrock ni a un proveedor de memoria. El MVP mantiene PostgreSQL como
autoridad de datos dinámicos, Knowledge Base como autoridad documental y tools
WCS como frontera segura de ejecución.

La entrega se agrupa en pocos incrementos de alto valor. Cada incremento puede
contener varios issues Jira relacionados y se integra mediante un único PR
coherente para reducir el costo operativo de revisión, merge y deploy.

## Estado de implementación y regla de avance

El control plane actual permite consultar evidencia sanitizada, registry, mapa,
simulaciones y preflight, además de crear DRAFTs, editar una definición como
nueva versión inmutable, promover lifecycle y activar rutas por contexto. Este
PR completa la parte de authoring guiado de la UI: los agentes, versiones y
asignaciones se seleccionan desde el registry, reduciendo errores manuales.

WCS-122 (pedidos y Mercado Pago Sandbox) queda fuera de este slice. Su
implementación puede avanzar aislada, pero no habilita pagos por sí sola ni
reemplaza los smoke tests de WCS-120 y WCS-121.

WCS-140 completa el siguiente tramo: las cuatro llamadas generativas actuales
(`conversation-router`, `response-generation`, `response-humanization` y
`conversation-summarizer`) tienen configuración ejecutable prevista en
PostgreSQL y baseline `1.0.0`. Esto reemplaza para esos llamados el antiguo plan
optativo de Bedrock Prompt Management descrito en PR 1; véase
[`ADR-043`](decisions/043-sql-agent-invocation-versions.md). Los cambios de
versión requieren primero desplegar esta base y aplicar Flyway V27; luego,
editar/activar perfiles versionados no requiere redeploy de la API.

La entrega 2 del ciclo de evaluación de WCS-140 hace útil y segura la
comparación de versiones: exige el mismo agente lógico, dataset y cobertura
única de escenarios; entrega deltas de calidad, un resultado descriptivo y
comparación por escenario. Las métricas especializadas sólo se comparan cuando
usan la misma muestra. Costo, tokens y latencia siguen separados de calidad;
no hay promoción automática ni afirmación estadística. El envelope de evidencia
se versiona como `wcs.agent-evaluation-evidence.v2`; no requiere Flyway ni
Terraform. Véanse [`ADR-020`](decisions/020-agent-evaluation-comparison.md) y
[`ADR-021`](decisions/021-agent-evaluation-evidence-export.md).

La entrega 3 cierra el paso de evaluación a promoción: `EVALUATED` y
`APPROVED` requieren enlazar un run de la versión activa y un run de la
candidata. El backend verifica mismo agente, dataset y cobertura de escenarios,
que el run candidato corresponde a la versión/suite que se revisa y que el
baseline pertenece a una versión actualmente activa. La evidencia y el
assessment descriptivo se guardan junto al evento de auditoría (Flyway V29) y
se pueden revisar desde el backoffice. La aprobación continúa siendo humana y
autorizada; no se inventan thresholds ni se aprueba automáticamente por un
resultado favorable. Requiere Flyway, no Terraform ni AppConfig.

## PR 1 — Runtime de prompts (histórico, supersedido por WCS-140)

Este plan inicialmente proponía Bedrock Prompt Management. ADR-043 lo
reemplaza para las llamadas conversacionales por configuración versionada en
PostgreSQL, alineada con el authoring/lifecycle del registry existente. No
implementar los pasos siguientes de esta sección como una segunda fuente de
verdad.

Incluía:

- `PromptRegistry` provider-neutral.
- Proveedor actual empaquetado como fallback.
- Proveedor opcional de Bedrock Prompt Management con versiones inmutables.
- Hash y versión en observabilidad sin contenido conversacional.
- IAM allowlisted para `GetPrompt`.
- Contrato de configuración y rollback documentado.
- Tests unitarios y validación de arranque/fallo cerrado.

No cambia el comportamiento actual mientras el proveedor sea `classpath`.

## PR 2 — Definición ejecutable y activación de agentes

Incluye:

- Snapshot ejecutable de agente con modelo, prompts, límites, schemas, tools y
  fuentes autorizadas.
- Resolución por agente, ambiente, canal y caso de uso.
- Estados `DRAFT`, `VALIDATED`, `PUBLISHED`, `RETIRED` y activación separada.
- Feature flags con kill switch, canary y rollback.
- Validación de allowlists antes de ejecutar.
- Contratos de eventos para agente, versión, operación, latencia, tokens,
  costo, resultado y fallback.

El runtime seguirá usando casos de uso determinísticos; el LLM no genera SQL
ni recibe credenciales.

El runtime conecta el snapshot SQL con routing, generación grounded de
`GENERAL_SUPPORT`, humanización de hechos de catálogo y resumen conversacional.
`LlmClient` mantiene compatibilidad con adapters anteriores; Bedrock aplica el
modelo, prompt, hash, parámetros, límites y timeout de la versión activa. El
catálogo y las reglas transaccionales siguen determinísticos.

## PR 3 — Backoffice operativo del control plane

Incluye en el mismo repositorio React/TypeScript:

- listado de agentes, versiones y estado de publicación;
- visualización de system prompt, user prompt, variables, modelo, parámetros,
  esfuerzo, input/output schema, tools y fuentes, con permisos adecuados;
- comparación entre versión estable y candidata;
- mapa de agentes por caso de uso y flujo de ejecución;
- activación, desactivación y rollback protegidos por autorización e
  idempotencia;
- estado explícito de operaciones no implementadas, read-only o preview.

La API será contract-first y no expondrá secretos, conversaciones completas ni
PII. La versión estable siempre se mostrará separada de una candidata.

El corte cubre el listado, mapa, simulación, preflight, authoring, clonación,
edición por nueva DRAFT, lifecycle, activación protegida, kill switch, rollback,
auditoría y trazas runtime persistidas. La pantalla distingue controles
read-only de mutaciones protegidas y conserva la autoridad en el backend.

La siguiente evidencia necesaria es operativa: smoke autenticado en el
ambiente objetivo, validación de las migraciones V19/V20 y habilitación gradual
de `wcs.agent-registry.authoring-write-enabled` y
`wcs.agent-registry.activation-write-enabled`.

## PR 4 — Evaluación, observabilidad y decisión de calidad

Incluye:

- datasets sintéticos por caso de uso;
- evaluaciones online y offline con criterios de grounding, intención,
  seguridad, respuesta válida, handoff y costo;
- comparación de agentes/modelos/prompts y scorecards por versión;
- dashboards Grafana y consultas por agente, caso, canal, resultado, tokens,
  costo, latencia, fallos y tasa de fallback;
- shadow mode y rollout controlado, sin publicar respuestas candidatas;
- evidencia reproducible para promover o retirar versiones.

## Orden de casos core

1. Router de intención y fallback seguro.
2. Catálogo y disponibilidad con PostgreSQL.
3. Conocimiento documental con Knowledge Base.
4. Humanización grounded de respuestas.
5. Handoff humano con contexto sanitizado.
6. Memoria de sesión, resumen y preferencias.
7. Pedidos, carrito y pagos, sólo después de definir autorización y fuentes.

## Fuera del primer ciclo

- MCP conectado directamente a la base productiva.
- LLM con SQL libre o autonomía para elegir acciones sensibles.
- AgentCore Memory como dependencia obligatoria.
- Editor de prompts sin workflow de publicación y evaluación.
- Multi-agente sin límites de costo, latencia y permisos.

MCP read-only, AgentCore Memory y nuevas estrategias de recuperación se podrán
evaluar detrás de adapters después de estabilizar estos contratos.

## Gates de cada entrega

- issue Jira aceptado y página canónica actualizada;
- tests unitarios, integración y contrato proporcionales al cambio;
- `git diff --check`, compilación y CI verdes;
- evidencia de configuración sin secretos;
- smoke de health y del caso de uso afectado;
- revisión de IAM y de cualquier cambio Terraform;
- rollback explícito y no destructivo.
