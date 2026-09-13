# ADR-033 — Ejecución gobernada por el snapshot versionado del agente

- Owner: Product / Tech Lead
- Status: `Accepted`
- Last reviewed: 2026-09-12
- Related Jira: `WCS-45` y el siguiente slice de ejecución del control plane
- Related documents: [`agent-platform-delivery-plan.md`](../agent-platform-delivery-plan.md), [`ai.md`](../ai.md), [`architecture.md`](../architecture.md)

## Contexto

WCS ya podía resolver una activación y construir una definición inmutable,
pero esa definición sólo aparecía en eventos operativos. El modelo, el prompt,
los límites y los schemas persistidos todavía no gobernaban la llamada real a
un proveedor de IA. Eso impedía comparar de forma confiable la versión que se
observaba con la versión que efectivamente se ejecutaba.

La evolución debe conservar el chatbot actual como fallback. En particular,
un error del registry, un prompt ausente o un hash incorrecto no puede dejar
sin respuesta a un cliente ni habilitar un prompt diferente al aprobado.

## Decisión

1. `AgentRuntimeDefinition` es el snapshot de sólo lectura que gobierna una
   ejecución versionada. El snapshot se obtiene únicamente de una activación
   válida y de una versión publicable.
2. `LlmClient` agrega una operación compatible que recibe ese snapshot. El
   método anterior permanece disponible para el flujo actual y para adapters
   que aún no implementen ejecución versionada.
3. El adapter Bedrock aplica desde el snapshot el `modelId`, `temperature`,
   `topP`, `maxOutputTokens` y `timeout` efectivo por request. El límite de
   entrada se mantiene acotado por la configuración global y por una
   aproximación conservadora del presupuesto de tokens de la definición.
4. El adapter resuelve el prompt indicado por `systemPromptVersion` y exige
   que el SHA-256 calculado coincida con `systemPromptHash`. Ante un mismatch,
   la ejecución falla cerrada y el orquestador conserva el fallback vigente.
5. Bedrock Prompt Management acepta una versión numérica solicitada por un
   snapshot. Los nombres lógicos históricos del clasificador siguen usando la
   referencia configurada para mantener compatibilidad.
6. La metadata del contrato —agente, versión, prompt, schemas, límites,
   modelo, tokens, costo, latencia y timeout— se registra sin contenido de
   prompts, mensajes, respuestas, secretos ni PII.
7. La primera integración se aplica a la generación grounded de `GENERAL_SUPPORT`.
   El router sigue usando su configuración global y `catalog-specialist` sigue
   consultando PostgreSQL mediante una tool determinística. La migración de
   cada paso del plan a un agente independiente queda para el siguiente slice.

## Flujo

```text
activación (agentId, ambiente, canal, caso de uso)
                    |
                    v
        AgentRuntimeDefinitionResolver
                    |
                    v
          snapshot inmutable validado
                    |
                    +--> prompt version + hash
                    +--> model + inference limits
                    +--> input/output schema metadata
                    +--> timeout + budget metadata
                    |
                    v
             LlmClient versionado
                    |
                    v
          Bedrock Converse / adapter futuro
```

La respuesta del catálogo permanece determinada por PostgreSQL. El modelo
puede redactar una respuesta grounded cuando el caso de uso tiene un snapshot
activo, pero no obtiene SQL, credenciales ni autoridad para inventar hechos.

## Fallos y rollback

- `activation-enabled=false` evita consultar el registry y conserva el flujo
  anterior.
- Activación ausente, kill switch, versión no aprobada o registry indisponible
  producen fallback tipado.
- Prompt no encontrado, versión Bedrock inválida, provider no compatible o
  hash incorrecto producen fallback de ejecución; no se usa silenciosamente
  otro prompt del agente.
- El timeout por request es el menor entre el timeout del agente y el límite
  global de `wcs.ai.request-timeout`.
- El rollback operativo es volver `wcs.agent-runtime.activation-enabled` a
  `false` o desactivar la activación específica. No se modifican migraciones,
  versiones inmutables ni prompts publicados.

## Observabilidad

`AGENT_ROUTED`, `AGENT_EXECUTION_STARTED` y `AI_USAGE_RECORDED` pueden
correlacionarse por request/correlación y muestran, cuando existe una
definición activa:

- `agentId` y `agentVersion`;
- `modelProvider`, `model` y `promptVersion`/`promptHash`;
- `inputSchemaVersion` y `outputSchemaVersion`;
- `inputTokens`, `outputTokens`, `totalTokens`, costo estimado y latencia;
- timeout y límites declarados por la versión.

El costo sigue siendo estimado con el pricing configurado para el proveedor.
La presencia de metadata no demuestra por sí sola que una activación sea
apta para producción: la promoción requiere evaluación, privacidad, calidad y
rollback revisados.

## Fuera de alcance

- edición o publicación desde el backoffice;
- selección libre de SQL, MCP o tools por el modelo;
- activación automática por calidad o costo;
- prompts enviados desde el request del usuario;
- uso obligatorio de AgentCore Memory, LangChain o LangGraph;
- migración automática de todos los steps del plan a Bedrock.
