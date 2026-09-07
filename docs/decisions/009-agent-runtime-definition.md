# ADR-009 — Definición ejecutable inmutable de agentes

- Status: `Accepted`
- Related Jira: `WCS-51`
- Related ADRs: [`005-agent-registry-contract.md`](005-agent-registry-contract.md), [`006-agent-registry-persistence.md`](006-agent-registry-persistence.md), [`007-agent-activation-resolution.md`](007-agent-activation-resolution.md), [`008-agent-runtime-activation.md`](008-agent-runtime-activation.md)

## Contexto

WCS ya puede resolver una activación por ambiente, canal y caso de uso. Esa
resolución sólo devuelve `agentId + agentVersion`; no es suficiente para
permitir que una futura ejecución especializada consuma modelo, límites,
contratos o tools sin validar la versión persistida.

La definición debe ser segura para el runtime y no debe convertir prompts,
conversaciones o datos del cliente en configuración ejecutable. También debe
mantener el fallback determinístico mientras el control plane evoluciona.

## Decisión

1. `AgentRuntimeDefinitionResolver` recibe la `AgentActivationKey` y consulta
   primero `AgentActivationResolver`.
2. Una activación en fallback nunca consulta ni expone una definición.
3. Para una activación activa se carga exactamente `agentId + agentVersion`.
4. La versión debe coincidir con la referencia y pasar `canBeActivated()`;
   actualmente esto exige el estado `APPROVED`.
5. `AgentRuntimeDefinition` es un snapshot inmutable que contiene proveedor y
   modelo, parámetros de inferencia, límites, contratos, políticas y
   allowlists. Las colecciones se copian de forma inmutable.
6. Sólo se conserva metadata de prompt (`systemPromptVersion` y hash); nunca
   se incluye el contenido del prompt ni información de conversación.
7. Cualquier ausencia, mismatch, estado no publicable, dato inválido o error
   del registry se transforma en un fallback tipado y sanitizado.
8. La resolución emite eventos mínimos `AGENT_DEFINITION_RESOLVED` o
   `AGENT_DEFINITION_RESOLUTION_FALLBACK`, sin prompt, PII ni detalles de
   excepciones.

## No decidido por este ADR

- ejecución dinámica de prompts o tools;
- selección real de proveedores/modelos en el `ConversationOrchestrator`;
- backoffice, evaluación offline, canary o MCP;
- activación productiva de `catalog-specialist`;
- migraciones nuevas o cambios en las tablas del registry.

## Consecuencias

- La futura ejecución recibirá un contrato validado y estable, no una entidad
  JPA ni una referencia mutable.
- Un registry incompleto o temporalmente indisponible no interrumpe el chat.
- Los límites del modelo y las allowlists se validan antes de integrar tools.
- La implementación no cambia las respuestas actuales porque todavía no se
  conecta al ejecutor de casos de uso.

## Rollback

No hay migración ni cambio de configuración productiva. Si la implementación
se despliega accidentalmente, dejar de invocar `AgentRuntimeDefinitionResolver`
o revertir el artefacto conserva el flujo de WCS-50. La flag
`wcs.agent-runtime.activation-enabled=false` continúa siendo el rollback del
runtime de activaciones.
