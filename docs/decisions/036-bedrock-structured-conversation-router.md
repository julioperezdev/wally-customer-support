# ADR-036 — Router conversacional estructurado con Bedrock

- Owner: Product/Tech Lead
- Status: `Accepted`
- Last reviewed: 2026-09-16
- Related Jira: `WCS-130`
- Related documents: [`ai.md`](../ai.md), [`architecture.md`](../architecture.md)

## Contexto

Las expresiones de un cliente no siguen el formato de los comandos internos.
Puede decir “sumame dos de esos”, escribir con errores o referirse a un
producto mencionado anteriormente. Un parser de texto determinístico cubre
los comandos conocidos, pero no escala como intérprete general de conversación.

Al mismo tiempo, el LLM no debe tener acceso directo a PostgreSQL ni poder
ejecutar SQL, pagos, cambios de stock u otras acciones sensibles.

## Decisión

Bedrock se usa como router semántico. Recibe el mensaje y un contexto acotado y
propone un JSON con:

- `intent`: caso de uso conversacional;
- `action`: operación de un catálogo cerrado;
- `confidence`: confianza numérica;
- `catalogQuery`: filtros normalizados;
- `quantity`: cantidad acotada;
- `missingParameters`: datos que deben aclararse.

El backend es la autoridad. Valida JSON, acción, confianza mínima, parámetros,
ownership, existencia, stock, precio e idempotencia y luego invoca el servicio
interno existente. El modelo nunca genera SQL, selecciona clases, elige un
repositorio arbitrario ni llama herramientas directamente.

Los comandos determinísticos de carrito se mantienen como camino prioritario.
El router sólo actúa cuando no existe un comando determinístico y el proveedor
Bedrock está habilitado. El proveedor mock continúa siendo útil para tests.

## Compatibilidad y fallback

El contrato `conversation-intent-v3` agrega `action`, `quantity` y
`missingParameters`. Si un prompt administrado anterior omite `action`, el
parser la deriva de `intent`; así se conserva compatibilidad durante la
migración de Prompt Management. Una respuesta inválida, una acción desconocida
o una confianza insuficiente produce aclaración o fallback seguro y nunca una
operación sensible.

## Consecuencias

La conversación puede mapear lenguaje natural a carrito, catálogo, pagos,
políticas y handoff sin duplicar la lógica de negocio ni acoplarla a Bedrock.
Cada decisión puede auditarse con intent, action, confidence, versión de
workflow y metadatos sanitizados. A cambio, el routing natural agrega latencia y
costo de inferencia; por eso los comandos determinísticos y los límites de
confianza se conservan.

## Fuera de alcance de este ADR

- SQL generado por el modelo o MCP de PostgreSQL en producción.
- Agentes autónomos con permisos generales.
- Selección dinámica de modelos o tools sin allow-list.
- Cambiar el proveedor productivo de AppConfig o desplegar infraestructura.
