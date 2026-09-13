# ADR-010 — Integración observacional de la definición de agente

- Status: `Accepted`
- Related Jira: `WCS-52`
- Related ADRs: [`008-agent-runtime-activation.md`](008-agent-runtime-activation.md), [`009-agent-runtime-definition.md`](009-agent-runtime-definition.md)

ADR-033 amplía esta decisión: la metadata continúa siendo sanitaria y
observable, y el snapshot activo también puede gobernar la generación de
`GENERAL_SUPPORT` bajo la flag de runtime. La ejecución versionada no habilita
por sí sola catálogo dinámico, tools arbitrarias ni activación productiva.

## Contexto

WCS-50 conecta la activación al orquestador y WCS-51 valida la definición
persistida. En el alcance inicial de este ADR sólo se observaba la definición;
ADR-033 agrega una ejecución acotada para `GENERAL_SUPPORT`, protegida por la
misma flag y por la validación del snapshot. El control plane todavía no
habilita tools arbitrarias, catálogo dinámico ni canary productivo.

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
- La respuesta del bot sólo depende del modelo o prompt del registry en el
  alcance acotado de `GENERAL_SUPPORT` cuando existe una activación válida;
  los demás casos de uso conservan su comportamiento actual.
- La validación real del flujo requiere una activación aprobada y un entorno
  controlado; producción conserva la flag deshabilitada.
- No se agregan migraciones ni cambios de infraestructura.

## Rollback

Publicar `wcs.agent-runtime.activation-enabled=false` evita la consulta de
activación y definición y deja el flujo determinístico anterior. No se borran
versiones ni activaciones del registry.
