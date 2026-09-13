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

## PR 1 — Runtime de prompts y foundation del control plane

Incluye:

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
