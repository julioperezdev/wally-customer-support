# ADR-011 — Primer límite ejecutable para `catalog-specialist`

- Status: `Accepted`
- Date: 2026-09-07
- Related Jira: `WCS-53`
- Supersedes: ninguna

## Contexto

WCS ya puede resolver una definición de agente publicada, pero todavía no
debe ejecutar prompts o tools dinámicas desde esa definición. El catálogo es
el caso de uso más sensible a alucinaciones: precio, stock, talle y SKU deben
provenir de PostgreSQL.

## Decisión

Se incorpora `CatalogSpecialistExecutor` como primer límite de ejecución. Sólo
acepta una `AgentRuntimeDefinition` activa cuyo `agentId` sea
`catalog-specialist` y cuyo allowlist contenga `catalog.search`. Su request
tipado contiene `CatalogQuery`, historial acotado y el último mensaje; no
contiene SQL ni instrucciones ejecutables.

El executor delega en `CatalogConversationService`, que conserva las
consultas parametrizadas y el formateo de respuestas existente. No invoca
Bedrock adicionalmente, no genera SQL y no altera la fuente de verdad.

Si el límite no puede ejecutarse, el orquestador vuelve a la ruta determinística
legacy. El usuario nunca recibe una excepción, SQL, secreto ni contenido
operativo interno.

## Consecuencias

Positivas:

- Permite medir una ejecución especialista con `agentId`, versión, resultado y
  latencia.
- Establece un contrato reutilizable antes de agregar ejecución de modelos o
  herramientas adicionales.
- Mantiene rollback funcional sin cambiar la configuración productiva actual.

Costos:

- Existe temporalmente una ruta especialista y una ruta legacy.
- El contrato todavía no ejecuta prompts o tool use de Bedrock; eso requiere
  una tarea posterior con evaluación y activación controlada.

## Fuera de alcance

MCP para PostgreSQL, generación de SQL, loops autónomos, selección dinámica de
modelos, backoffice, RAG y activación productiva.
