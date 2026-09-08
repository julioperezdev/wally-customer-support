# ADR-028 — Comparación shadow efímera y candidata Bedrock acotada

Status: `Accepted`
Date: 2026-09-08
Related Jira: `WCS-103`, `WCS-104`, `WCS-105`
Related decisions: `ADR-009`, `ADR-011`, `ADR-027`

## Contexto

WCS ya puede ejecutar una candidata en modo shadow, pero todavía no tenía una
comparación de calidad ni un adapter Bedrock conectado a esa frontera. La
respuesta activa debe continuar siendo la única respuesta al usuario y el
experimento no puede almacenar conversaciones, prompts o respuestas.

## Decisión

La comparación se realiza en memoria mediante SHA-256 sobre la respuesta
activa y la candidata. Sólo se publica el resultado categórico `MATCH`,
`MISMATCH` o `UNKNOWN`; el digest no se persiste ni se escribe en logs.

El executor Bedrock es un adapter opcional seleccionado por
`wcs.agent-runtime.shadow-provider=bedrock`. Sólo soporta el caso de uso
`CATALOG_SEARCH`, valida que el modelo del registry coincida con `wcs.ai`,
respeta el límite de salida de la definición y usa `MeasuredLlmClient` para
obtener tokens, latencia y costo. Su entrada se limita a filtros de catálogo
normalizados y una referencia source-backed acotada; no recibe el mensaje
crudo, identidad del cliente, SQL, MCP, conocimiento no autorizado ni acceso
al outbox.

El valor por defecto es `noop`. La bandera de shadow y la activación del
registry continúan apagadas. Un error, timeout, mismatch o exceso de límite
produce evidencia sanitizada y no altera el resultado activo.

## Consecuencias

- Se pueden medir coincidencias y divergencias sin retener texto de clientes.
- Bedrock se reutiliza detrás del port de metadata ya instrumentado.
- La calidad semántica todavía requiere una evaluación posterior; `MATCH` es
  igualdad normalizada de texto y no una aprobación editorial.
- No hay promoción automática, canary, MCP, SQL generado ni backoffice de
  prompts en esta fase.

## Rollback

Publicar `wcs.agent-runtime.shadow-enabled=false`,
`wcs.agent-runtime.shadow-provider=noop` y
`wcs.agent-runtime.activation-enabled=false`. No requiere migraciones,
Terraform ni cambios de secretos.
