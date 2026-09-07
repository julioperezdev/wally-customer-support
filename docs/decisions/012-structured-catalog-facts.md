# ADR-012 — Hechos estructurados separados de la presentación del catálogo

- Status: `Accepted`
- Date: 2026-09-07
- Related Jira: `WCS-54`
- Supersedes: ninguna

## Contexto

El límite de WCS-53 todavía devolvía el texto formateado por el servicio de
catálogo. Eso funciona para el MVP, pero mezcla dos responsabilidades: saber
qué devolvió PostgreSQL y decidir cómo expresarlo en Telegram o WhatsApp.

## Decisión

El caso de uso entrega `CatalogSearchResult`, con un estado explícito y una
lista de `CatalogFact`. Cada hecho contiene sólo nombre, SKU, talle, color,
precio, moneda y stock. Los hechos se construyen desde los modelos de dominio
obtenidos por la consulta parametrizada; nunca desde un prompt o una respuesta
de LLM.

`CatalogResponseFormatter` es una política pura de presentación. Mantiene el
texto actual y resuelve disponibilidad, precio, talle, color, desambiguación y
alternativas a partir de hechos ya validados. Un futuro humanizador podrá
trabajar sobre esta salida estructurada, pero no podrá agregar campos de
negocio.

## Consecuencias

Positivas:

- La fuente de verdad queda separada del tono del canal.
- Los agentes pueden evaluarse con contratos estructurados y no sólo con texto.
- Se puede cambiar el formatter o agregar un humanizador sin cambiar la
  consulta SQL ni exponer la base al modelo.

Costos:

- Se mantienen temporalmente métodos legacy que devuelven texto para los
  canales existentes.
- Los estados y campos del contrato deben versionarse antes de habilitar
  generación dinámica.

## Fuera de alcance

Bedrock humanizer, generación de SQL, MCP, Knowledge Bases, selección dinámica
de modelos, cambios de canal, backoffice y activación productiva.
