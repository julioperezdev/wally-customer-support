# Índice documental — WCS

## Dirección y producto

- [`roadmap.md`](roadmap.md)
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
