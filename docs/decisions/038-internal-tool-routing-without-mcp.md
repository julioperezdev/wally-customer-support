# ADR-038 — Routing interno tipado sin MCP

## Estado

Aceptado para la primera implementación de routing de tools.

## Contexto

WCS necesita interpretar lenguaje natural, resolver casos de uso y consultar
datos dinámicos sin exponer SQL, repositorios ni secretos al modelo. El análisis
de `ai-tool-routing-mcp-analysis.md` considera MCP como una opción útil para
interoperabilidad entre clientes, pero no como una necesidad para el runtime
interno de una aplicación Spring.

## Decisión

WCS mantiene el router de workflows y agrega un registro interno
provider-neutral de tools tipadas:

- cada tool tiene nombre estable, descripción, versión de schema e input tipado;
- el registry rechaza nombres duplicados al iniciar la aplicación;
- la ejecución delega en servicios de aplicación allow-listed;
- el modelo puede proponer una intención y argumentos estructurados, pero el
  backend reconcilia filtros explícitos, valida el contrato y decide si ejecuta;
- `catalog.search` es la primera tool ejecutable y consulta PostgreSQL mediante
  el servicio existente;
- carrito, checkout, handoff, memoria y Knowledge Base siguen siendo
  capacidades del orquestador y sus adapters, no SQL generado por el modelo.

MCP no se incorpora al runtime actual. Si en el futuro un cliente externo
necesita consumir las mismas capacidades, se agregará un adapter MCP sobre el
registry existente, conservando autorización, validación, ownership,
observabilidad e idempotencia de WCS.

## Consecuencias

Se reduce la superficie de ataque y se preserva la trazabilidad de cada
operación. Bedrock Tool Use queda implementado como adapter opt-in para el
contrato `conversation.route`; permanece apagado por default para conservar el
comportamiento productivo actual. Esto permite evaluar primero la calidad del
contrato y del routing con tests determinísticos, Testcontainers y el fallback
existente, y luego activar un piloto controlado sin incorporar MCP.

La reconciliación determinística evita que una propuesta del modelo convierta
una categoría en nombre libre: `quiero un buzo` conserva
`productType=buzo`. Los valores explícitos de talle, color, SKU y precio tienen
prioridad sobre una inferencia del modelo.

## Fuera de alcance

- permitir que un LLM ejecute SQL o elija repositorios;
- implementar un mega-tool genérico como `query_database`;
- incorporar MCP sólo para resolver routing interno;
- activar nuevas definiciones de agentes en producción sin preflight,
  feature flag y evidencia de evaluación.

## Evidencia

- `WcsToolRegistry` y `CatalogSearchTool` implementan el primer contrato;
- `CatalogSpecialistExecutor` ejecuta únicamente `catalog.search` autorizado;
- `CatalogQueryParser` reconcilia filtros explícitos con la propuesta del modelo;
- `V23__complete_core_agent_runtime_metadata.sql` completa el baseline de
  `knowledge-specialist` y `checkout-specialist` sin habilitar activaciones;
- `mvn -B verify` ejecutado localmente: 428 tests, 0 fallos.
