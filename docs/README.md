# Índice documental — WCS

## Dirección y producto

- [`roadmap.md`](roadmap.md)
- [`agent-platform-roadmap.md`](agent-platform-roadmap.md): propuesta de plataforma de agentes, registry, evaluación, feature flags y backoffice.
- [`functional-requirements.md`](functional-requirements.md)
- [`specification-baseline.md`](specification-baseline.md)
- [`baselines/wcs-baseline-2026-09-07.md`](baselines/wcs-baseline-2026-09-07.md): punto de restauración antes de la plataforma de agentes.

## Diseño técnico

- [`architecture.md`](architecture.md)
- [`data-model.md`](data-model.md)
- [`ai.md`](ai.md)
- [`conversational-memory-and-agentcore-plan.md`](conversational-memory-and-agentcore-plan.md): contexto conversacional, memoria, Knowledge Base y evaluación opcional de AgentCore.
- [`privacy-retention.md`](privacy-retention.md): clasificación, límites, aislamiento, borrado y retención de memoria conversacional.
- [`decisions/`](decisions/)
  - [`002-static-knowledge-and-dynamic-data.md`](decisions/002-static-knowledge-and-dynamic-data.md): Knowledge Base para documentos y tools para datos dinámicos.
  - [`003-conversational-memory-boundary.md`](decisions/003-conversational-memory-boundary.md): PostgreSQL como memoria inicial y AgentCore como adapter opcional.
  - [`004-agent-platform-boundary.md`](decisions/004-agent-platform-boundary.md): orquestador, agentes especialistas, tools determinísticas y límites de MCP.
  - [`005-agent-registry-contract.md`](decisions/005-agent-registry-contract.md): versiones inmutables, lifecycle, activación, kill switch y rollback.
  - [`006-agent-registry-persistence.md`](decisions/006-agent-registry-persistence.md): persistencia PostgreSQL del registry y referencias de activación.
  - [`007-agent-activation-resolution.md`](decisions/007-agent-activation-resolution.md): resolución por contexto y fallback seguro.
  - [`008-agent-runtime-activation.md`](decisions/008-agent-runtime-activation.md): consumo opcional del resolver desde el runtime con rollback seguro.
  - [`009-agent-runtime-definition.md`](decisions/009-agent-runtime-definition.md): definición ejecutable inmutable y validación de allowlists.
  - [`010-agent-definition-runtime-observability.md`](decisions/010-agent-definition-runtime-observability.md): integración observacional del snapshot al orquestador.
  - [`011-catalog-specialist-deterministic-boundary.md`](decisions/011-catalog-specialist-deterministic-boundary.md): primer límite ejecutable con tool de catálogo determinística.
  - [`012-structured-catalog-facts.md`](decisions/012-structured-catalog-facts.md): hechos tipados separados de la presentación de respuesta.
  - [`013-safe-response-humanization.md`](decisions/013-safe-response-humanization.md): política de presentación reemplazable sin autoridad sobre los hechos.

## Calidad y operación

- [`testing-strategy.md`](testing-strategy.md)
- [`operations.md`](operations.md)
- [`observability.md`](observability.md): Grafana local, CloudWatch y eventos operativos.
- [`queries.md`](queries.md)
- [`../infra/README.md`](../infra/README.md): base AWS, state y reglas de activación.

## Colaboración

- [`documentation-system.md`](documentation-system.md)
- [`agents/playbook.md`](agents/playbook.md)
- [`agents/jira-github-traceability.md`](agents/jira-github-traceability.md)
- [`../planning/jira-backlog.md`](../planning/jira-backlog.md)
