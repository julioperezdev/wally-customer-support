# ADR-043 — Configuración ejecutable de agentes Bedrock en PostgreSQL

- Owner: Product/Tech Lead
- Status: `Accepted`
- Last reviewed: 2026-09-22
- Related Jira: `WCS-140`
- Supersedes: `ADR-032` for current conversational Bedrock calls
- Related documents: [`ai.md`](../ai.md), [`data-model.md`](../data-model.md), [`agent-platform-roadmap.md`](../agent-platform-roadmap.md)

## Contexto

WCS ya tenía lifecycle, activaciones y trazas de agentes en SQL, pero las
llamadas a Bedrock armaban sus prompts y parámetros desde código/configuración
global. Esto obligaba a redeployar para iterar y dificultaba comparar versiones
con costos correctos. El objetivo es preservar el comportamiento y los
contratos de negocio, haciendo declarativa y versionada la configuración de las
cuatro llamadas generativas actuales.

## Decisión

1. PostgreSQL, mediante `wcs.agent_versions`, es fuente de verdad de cada
   configuración declarativa e inmutable. Las activaciones existentes en
   `wcs.agent_activations` seleccionan versión por ambiente, canal y caso de uso.
2. La baseline inicial es `1.0.0` para `conversation-router`,
   `response-generation`, `response-humanization` y `conversation-summarizer`.
3. SemVer expresa impacto dentro de una identidad de agente: PATCH para cambios
   pequeños compatibles, MINOR para capacidades compatibles y MAJOR para cambios
   incompatibles de contrato o responsabilidad. Un `agentId` nuevo comienza su
   propia serie en `1.0.0`; `agent_version` continúa como clave interna SQL.
4. Cada versión persiste system prompt, user template, JSON schemas,
   provider/model, temperatura, `topP`, reasoning effort, límites de input/output,
   timeout, presupuesto y pricing versionado con tarifas de entrada/salida.
   Modelo y tarifas se versionan juntos. Credenciales, región de plataforma y
   código ejecutable no se guardan en estas filas.
5. El backend lee la activación y versión al iniciar cada llamada, sin cache,
   para que una activación o rollback tenga efecto en la siguiente inferencia.
   Configuración ausente o inválida no se ejecuta y conserva el camino
   compatible con un evento sanitizado.
6. La edición crea una DRAFT inmutable por el control plane autenticado. Se
   reutilizan las evaluaciones, lifecycle, auditoría, aprobaciones y activaciones
   existentes; una versión publicada nunca se modifica.
7. Templates sólo interpolan placeholders allowlisted. WCS conserva autorización,
   reglas de negocio, SQL, tools, stock, precio, carrito, pagos, idempotencia y
   validación de resultados.

## Alcance de la primera entrega

| Perfil | Operación | Caso | Contrato aplicado |
| --- | --- | --- | --- |
| Router | `conversation.intent.classify` | `ROUTING` | Schema de decisión conectado al contrato de tool y validado por WCS |
| Soporte | `conversation.reply.generate` | `GENERAL_SUPPORT` | Texto sujeto a las validaciones y fallback actuales |
| Humanizador catálogo | `conversation.response.humanize` | `CATALOG_SEARCH` | Facts validator determinístico, sin relajarlo |
| Resumen memoria | `conversation.summary.generate` | `CONVERSATION_SUMMARY` | Mensajes redactados/acotados; no fuente transaccional |

Knowledge Base Retrieve queda fuera: es recuperación, no inferencia generativa
con prompts system/user. Los schemas de los otros tres perfiles quedan
versionados como contrato de authoring/evaluación; no se afirma validación JSON
Schema genérica en runtime donde la operación vigente es texto libre. Nuevos
flujos, código o tools requieren cambios de backend y contratos propios; no se
pueden desplegar sólo cambiando un prompt.

## Cambio, evaluación y rollback

El primer release requiere código y Flyway `V27`. Luego, el ciclo de cambio es:
clonar versión activa → editar DRAFT → correr evaluación con el mismo dataset →
comparar calidad/grounding, errores, latencia, tokens y costo → registrar los
runs comparados en `EVALUATED` → revisión y aprobación humana → activar por
dimensión. El backend verifica mismo agente, dataset y cobertura, la identidad
de la versión candidata y que el run baseline pertenezca a una versión activa.
Los IDs de los runs y el assessment descriptivo quedan enlazados al evento de
auditoría mediante Flyway `V29`. No hay promoción automática ni umbrales
universales implícitos. Rollback es una activación nueva a una versión
previamente aprobada, no una mutación destructiva.

El primer patch del humanizador se entrega mediante `V28` como SemVer `1.0.1`.
Agrega `required_facts` al prompt para enumerar explícitamente los hechos de
cada variante antes de redactar, mantiene el validator determinístico y deja
la activación `1.0.0` como destino de rollback.

El límite de input se aproxima con cuatro caracteres por token, sujeto a un
techo seguro, porque el adapter Converse no limita con precisión los tokens de
entrada. Bedrock reporta el conteo real después de la llamada. Timeout SQL queda
acotado por el timeout global seguro de API. El pricing sólo es estimación, no
reemplaza la factura de AWS.

## Seguridad y privacidad

- Los prompts sólo se leen/escriben a través de rutas autenticadas/scoped del
  control plane; no se emiten en logs, métricas ni trazas.
- La telemetría guarda IDs/hashes de agente y prompt, modelo, uso, costo,
  latencia y error sanitizado, nunca el mensaje, prompt, respuesta o secreto.
- No guardar secretos, bearer tokens, SQL ejecutable, código o permisos IAM en
  las versiones de agente.
- Cada tool y acción continúa sujeto a allowlists y validadores de WCS.
- Este cambio no ejecuta migraciones en la base real; `V27` se aplicará por el
  flujo normal cuando se integre.

## Consecuencias

Después del release inicial, prompts/modelos/límites pueden evolucionar y
activarse sin rebuild, con historial SemVer y rollback trazable. Cada llamada
consulta activación y versión en PostgreSQL, agregando latencia y dependencia de
la disponibilidad de la BD. Una caché futura exige política de invalidación que
preserve la expectativa de hot update.
