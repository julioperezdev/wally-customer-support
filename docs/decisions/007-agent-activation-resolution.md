# ADR-007 — Resolución de activaciones con fallback seguro

- Owner: Product/Tech Lead
- Status: `Accepted`
- Related Jira: `WCS-49`
- Related decisions: [`005-agent-registry-contract.md`](005-agent-registry-contract.md), [`006-agent-registry-persistence.md`](006-agent-registry-persistence.md)

## Contexto

El registry ya puede persistir versiones y activaciones, pero el runtime no
debe consultar tablas directamente ni asumir que siempre existe una versión
activa. Antes de conectarlo al orquestador se necesita una frontera que
devuelva una referencia válida o un fallback explícito.

## Decisión

`AgentActivationResolver` recibe una clave compuesta por `agentId`, ambiente,
canal y caso de uso. Consulta la última activación persistida y produce un
resultado inmutable:

```text
ACTIVE   -> agentId + agentVersion exactos
FALLBACK -> sin referencia de agente + razón controlada
```

Las razones de fallback son `NOT_CONFIGURED`, `DISABLED`, `KILL_SWITCH` y
`REGISTRY_UNAVAILABLE`. La última activación tiene precedencia sobre registros
anteriores; por eso un kill switch no puede ser ignorado buscando la última
activación habilitada. Una excepción de infraestructura se convierte en
fallback y no se propaga al canal ni incluye el detalle de la excepción.

El resolver todavía no es invocado por `ConversationOrchestrator`. Esta
separación permite probar selección y degradación antes de habilitar tráfico
real.

## Consecuencias

- el runtime futuro podrá elegir agentes mediante un contrato de aplicación,
  no mediante JPA ni SQL;
- un registry vacío o temporalmente indisponible conserva el flujo seguro;
- los dashboards podrán distinguir configuración ausente, kill switch y fallo
  de infraestructura;
- la activación real, AppConfig y el rollout canary quedan para fases
  posteriores.
