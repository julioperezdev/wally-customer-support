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
  - [`014-agent-evaluation-contract.md`](decisions/014-agent-evaluation-contract.md): escenarios sintéticos versionados y evaluación determinística.
  - [`015-agent-evaluation-runner.md`](decisions/015-agent-evaluation-runner.md): ejecución determinística y métricas agregadas de una suite.
  - [`016-agent-evaluation-metadata.md`](decisions/016-agent-evaluation-metadata.md): metadata operativa separada para latencia, tokens y costo.
  - [`017-agent-evaluation-application-boundary.md`](decisions/017-agent-evaluation-application-boundary.md): frontera interna para ejecutar datasets y generar runs sanitizados.
  - [`018-agent-evaluation-persistence.md`](decisions/018-agent-evaluation-persistence.md): persistencia create-only de runs y escenarios sanitizados.
  - [`019-agent-evaluation-history-query.md`](decisions/019-agent-evaluation-history-query.md): consulta interna filtrable y paginada del histórico.
  - [`020-agent-evaluation-comparison.md`](decisions/020-agent-evaluation-comparison.md): comparación sanitizada de runs y deltas operativos.
  - [`021-agent-evaluation-evidence-export.md`](decisions/021-agent-evaluation-evidence-export.md): envelope versionado para exportar evidencia sanitizada.
  - [`022-agent-evaluation-retention-policy.md`](decisions/022-agent-evaluation-retention-policy.md): decisión determinística de retención sin purga automática.
  - [`023-agent-evaluation-trigger-spring-composition.md`](decisions/023-agent-evaluation-trigger-spring-composition.md): composición Spring con denegación por defecto.
  - [`024-agent-evaluation-control-plane-read-api.md`](decisions/024-agent-evaluation-control-plane-read-api.md): acceso provider-neutral y API read-only del control plane.
  - [`025-agent-evaluation-control-plane-jwt-security.md`](decisions/025-agent-evaluation-control-plane-jwt-security.md): JWT configurable sólo para el control plane, con scope exacto y rutas públicas preservadas.
  - [`026-agent-evaluation-trigger-http.md`](decisions/026-agent-evaluation-trigger-http.md): trigger HTTP autenticado, scopes separados e idempotencia.
  - [`028-shadow-quality-comparison-and-bedrock-candidate.md`](decisions/028-shadow-quality-comparison-and-bedrock-candidate.md): comparación efímera y candidata Bedrock cerrada por defecto.
  - [`029-shadow-quality-scorecard-and-observability.md`](decisions/029-shadow-quality-scorecard-and-observability.md): scorecard determinístico y observabilidad de shadow sin autoridad de activación.
  - [`030-controlled-shadow-rollout.md`](decisions/030-controlled-shadow-rollout.md): allowlist de ambiente y porcentaje determinístico para rollout shadow.

## Calidad y operación

- [`testing-strategy.md`](testing-strategy.md)
- [`operations.md`](operations.md)
- [`observability.md`](observability.md): Grafana local, CloudWatch y eventos operativos.
- [`pilot-runbook.md`](pilot-runbook.md): protocolo, límites, criterios y rollback del piloto WCS-23.
- [`pilot-report-template.md`](pilot-report-template.md): plantilla de evidencia y decisión del piloto.
- [`backoffice.md`](backoffice.md): panel React/TypeScript read-only para evaluación.
- [`queries.md`](queries.md)
- [`../infra/README.md`](../infra/README.md): base AWS, state y reglas de activación.

## Colaboración

- [`documentation-system.md`](documentation-system.md)
- [`agents/playbook.md`](agents/playbook.md)
- [`agents/jira-github-traceability.md`](agents/jira-github-traceability.md)
- [`../planning/jira-backlog.md`](../planning/jira-backlog.md)
