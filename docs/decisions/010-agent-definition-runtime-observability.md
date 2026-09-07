# ADR-010 — Integración observacional de la definición de agente

- Status: `Accepted`
- Related Jira: `WCS-52`
- Related ADRs: [`008-agent-runtime-activation.md`](008-agent-runtime-activation.md), [`009-agent-runtime-definition.md`](009-agent-runtime-definition.md)

## Contexto

WCS-50 conecta la activación al orquestador y WCS-51 valida la definición
persistida que podría ejecutarse en el futuro. Todavía no conviene permitir que
esa definición seleccione prompts, modelos o tools, porque el control plane no
tiene aún evaluación, backoffice ni canary.

## Decisión

1. El `ConversationOrchestrator` conserva una única flag de entrada:
   `wcs.agent-runtime.activation-enabled`.
2. Si la flag está deshabilitada o no existe canal, no consulta la definición.
3. Si la flag está habilitada, consulta la definición usando la misma clave de
   ambiente, canal y caso de uso que la activación.
4. El resultado se agrega sólo como metadata sanitizada a `AGENT_ROUTED` y
   `AGENT_EXECUTION_STARTED`.
5. Una definición activa puede aportar `agentId`, `agentVersion`, proveedor y
   modelo a los eventos; un fallback aporta únicamente estado y razón.
6. El switch actual de casos de uso sigue ejecutándose igual, incluso cuando la
   definición es activa o falla.

## Consecuencias

- Se puede medir qué definición habría sido utilizada en cada flujo antes de
  habilitar ejecución dinámica.
- La respuesta del bot no depende todavía del modelo o prompt del registry.
- La validación real del flujo requiere una activación aprobada y un entorno
  controlado; producción conserva la flag deshabilitada.
- No se agregan migraciones ni cambios de infraestructura.

## Rollback

Publicar `wcs.agent-runtime.activation-enabled=false` evita la consulta de
activación y definición y deja el flujo determinístico anterior. No se borran
versiones ni activaciones del registry.
