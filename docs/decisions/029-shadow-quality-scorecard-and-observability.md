# ADR-029 — Scorecard de calidad y observabilidad para shadow

## Estado

Aceptado para implementación en WCS-106, WCS-107 y WCS-108. El scorecard
queda disponible para revisión humana, pero no tiene autoridad para activar,
publicar o revertir una versión.

## Contexto

WCS ya puede ejecutar una candidata aislada y comparar su salida con la
respuesta activa usando sólo evidencia sanitizada. `MATCH`, `MISMATCH` y
`UNKNOWN` no son suficientes por sí solos para decidir una promoción: también
hay que controlar muestra, fallos, latencia, tokens y costo.

## Decisión

Se agrega un scorecard determinístico y provider-neutral con tres resultados:

* `INSUFFICIENT_EVIDENCE`: todavía no hay muestra u operación medible
  suficiente;
* `BLOCK`: existe una regresión o un límite excedido;
* `APPROVE_FOR_REVIEW`: la evidencia supera los límites y puede pasar a
  revisión humana.

Los valores iniciales seguros son:

| Métrica | Límite inicial |
| --- | ---: |
| muestra mínima | 20 ejecuciones |
| tasa de fallos | 10% |
| tasa de mismatch | 15% |
| tasa de unknown | 10% |
| latencia p95 | 5.000 ms |
| costo medio estimado | USD 0,010 por ejecución |

Los límites son configuración externa y pueden ser reemplazados por AppConfig.
Un valor operativo desconocido no se convierte en cero: impide aprobar la
revisión. Los contadores inconsistentes fallan cerrado como `BLOCK`.

## Privacidad y autoridad

El contrato sólo acepta agregados sanitizados. No contiene prompts, respuestas,
mensajes, teléfonos, tokens de autenticación, SQL, MCP ni PII. El resultado no
modifica PostgreSQL, AppConfig, el registry ni el canal de salida.

La aprobación del scorecard sólo habilita una revisión humana posterior. La
activación sigue requiriendo el flujo de registry, permisos separados,
evidencia y rollback.

## Observabilidad

Grafana agrega consultas para `AGENT_TRAFFIC_COMPARISON_RECORDED`, agrupadas por
agente, versión, modelo, canal, caso de uso y hora. Se visualizan las tasas de
MATCH/MISMATCH/UNKNOWN y las métricas de latencia, tokens y costo estimado.
`requestId` y la conversación pseudonimizada quedan reservados para diagnóstico
puntual, no para agrupaciones de dashboard.

## Rollback

No requiere migración ni infraestructura. Para detener cualquier candidato se
publica `wcs.agent-runtime.shadow-enabled=false` y
`wcs.agent-runtime.shadow-provider=noop`. El scorecard puede quedar desplegado
porque no modifica el runtime.
