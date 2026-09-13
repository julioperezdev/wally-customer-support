# ADR-031 — Prompt de intención versionado y guardrails configurables

**Estado:** Accepted  
**Relacionado:** WCS-20, `docs/ai.md`, `WCS — AI Models & Prompt Registry`

La decisión sobre almacenamiento del prompt fue ampliada por
[`ADR-032`](032-bedrock-prompt-management.md): Bedrock Prompt Management es
el proveedor productivo opt-in y este registry empaquetado se conserva como
fallback y rollback compatible.

## Contexto

El modelo Bedrock ya se selecciona mediante AppConfig, pero el prompt del
clasificador y algunos límites de inferencia estaban embebidos en Java. Eso
dificulta comparar versiones, auditar cambios y operar los límites sin volver
a compilar por cada ajuste permitido.

## Decisión

1. Los prompts aprobados se versionan como archivos Markdown dentro del
   artefacto (`src/main/resources/prompts/`).
2. `ClasspathPromptRegistry` carga sólo versiones con nombres seguros y
   calcula un SHA-256 estable de cada contenido.
3. AppConfig selecciona la versión (`wcs.ai.prompt.intent-version`) y los
   límites bounded de la inferencia; no almacena ni acepta texto de prompt
   arbitrario.
4. El adapter Bedrock registra `promptVersion` y `promptHash` en la metadata
   de uso, sin prompt, respuesta, conversación, secreto o PII.
5. La confianza mínima se aplica después de parsear el JSON, en el backend,
   mediante `wcs.conversation.guardrails.min-intent-confidence`. El LLM no
   puede bajar ese límite ni habilitar una acción.

## Límites iniciales

| Configuración | Default | Límite aplicado |
| --- | ---: | --- |
| `wcs.ai.prompt.intent-max-output-tokens` | 1024 | 1–1024 |
| `wcs.ai.prompt.intent-temperature` | 0.0 | 0–2 |
| `wcs.ai.prompt.max-input-characters` | 2000 | 1–2000 |
| `wcs.ai.prompt.max-history-messages` | 12 | 1–20 |
| `wcs.conversation.guardrails.min-intent-confidence` | 0.65 | 0–1 |

## Alternativas descartadas

- Guardar prompts completos en logs o en el request: expone contenido y
  elimina la trazabilidad del artefacto aprobado.
- Permitir que el LLM genere SQL, tools o el plan de ejecución: contradice la
  frontera determinística de WCS.
- Hacer que AppConfig contenga el prompt completo: dificulta revisión,
  fixtures, hash reproducible y rollback por commit.

## Rollout y rollback

El cambio es compatible con el runtime actual. Si una versión nueva no pasa
las pruebas o el smoke, se vuelve a la versión anterior de AppConfig; si falta
el archivo seleccionado, el arranque falla cerrado en lugar de ejecutar un
prompt desconocido.
