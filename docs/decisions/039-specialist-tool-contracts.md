# ADR-039 — Especialistas y contratos de tools provider-neutral

## Estado

Accepted for local-first implementation — 2026-09-18

## Contexto

WCS ya tiene un router estructurado y un límite ejecutable para catálogo, pero
el plan de ejecución sólo expresaba el owner y la capacidad. Eso dejaba la
allowlist, los schemas y la relación entre un caso de uso y su especialista
dispersos entre migraciones, prompts y código del orquestador.

La plataforma necesita poder incorporar distintos proveedores de modelos sin
permitir que un LLM elija arbitrariamente un servicio, genere SQL o ejecute una
operación sensible sin validación de WCS.

## Decisión

Se agrega una frontera interna provider-neutral con dos catálogos:

- `WcsToolContractCatalog` define los contratos versionados de las capacidades
  de WCS (`catalog.search`, stock, Knowledge Base, estado conversacional,
  carrito, checkout, handoff y fallback seguro).
- `AgentSpecialistRegistry` define cada especialista, su responsabilidad, los
  casos de uso soportados y la allowlist de tools.

`ConversationExecutionStep` transporta por cada step el nombre de la tool y
las versiones de los schemas de entrada y salida. El resolver rechaza una
definición que use un agente desconocido, una tool fuera de su allowlist o un
contrato inexistente. El orquestador vuelve a validar el plan antes de
ejecutarlo, porque la definición o la propuesta del modelo no es autoridad de
seguridad.

La primera tool ejecutable continúa siendo `catalog.search`, delegada al
servicio de catálogo existente. Los contratos de Knowledge Base, estado,
carrito, checkout, handoff y seguridad quedan declarados para validación y
observabilidad, pero no se convierten en implementaciones ficticias: cada uno
se habilitará cuando exista su wrapper tipado y sus pruebas contractuales.

## Reglas

1. El LLM no genera SQL ni decide permisos; sólo propone una intención o una
   operación que WCS valida.
2. Un agente desconocido, una tool no permitida o un schema incompatible usan
   el fallback seguro y dejan un evento estructurado de rechazo.
3. PostgreSQL sigue siendo el dueño de los datos dinámicos; Knowledge Base es
   el dueño de la información estática; checkout sólo opera mediante su adapter.
4. Los logs de tool incluyen agente, versión, tool, schemas, caso de uso,
   correlación y resultado sanitizado, sin conversación completa ni secretos.
5. La activación por ambiente y canal permanece detrás de la configuración de
   runtime; esta fase no modifica AppConfig, Terraform ni App Runner.

## Consecuencias

La allowlist y los schemas se pueden probar sin Bedrock, y los nuevos
proveedores pueden traducir estos contratos sin contaminar el dominio. El
catálogo central también hace visible qué capacidades faltan por implementar.

El costo es mantener versiones de schemas y completar wrappers para cada
capacidad antes de permitir su ejecución dinámica. Durante la transición,
algunos contratos existen como metadata de control mientras sus servicios
actuales siguen siendo invocados por el orquestador.

## Evidencia

- `WcsToolContractCatalogTest` verifica los contratos y sus schemas.
- `AgentSpecialistRegistryTest` verifica especialistas, allowlists y rechazo
  de tools o schemas incompatibles.
- `ConversationExecutionPlanFactoryTest` verifica que los steps lleven tool y
  versiones de schema.
