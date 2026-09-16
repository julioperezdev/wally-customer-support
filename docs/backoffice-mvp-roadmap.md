# WCS — Backoffice operativo MVP

Estado: `Accepted / In progress`  
Épica: [WCS-118](https://julioperezdev.atlassian.net/browse/WCS-118)

La entrega de la plataforma de agentes está dividida en dos niveles que no
deben confundirse: un panel read-only para observar el sistema y un control
plane protegido para crear, versionar, evaluar y activar agentes. Ambos niveles
están implementados en el código; el cierre de WCS-120 queda sujeto al smoke
autenticado y a la habilitación controlada de sus flags.

WCS-122 comenzó como un slice vertical aislado para validar pedidos y pagos sin
desplazar el control plane de agentes. Su proveedor queda en `mock` por defecto
y la activación de Mercado Pago Sandbox sigue bloqueada por sus propios gates
de credenciales, firma, migración y smoke. El número de Jira no determina por
sí solo el orden de desarrollo.

Este documento define el MVP del backoffice operativo de WCS. La entrega se
organiza en cuatro slices grandes para reducir la fricción de múltiples PR
pequeños. Cada slice incluye backend, frontend, pruebas, observabilidad,
documentación, rollout y rollback.

## Slices de entrega

| Slice | Jira | Alcance | Estado de planificación |
| --- | --- | --- | --- |
| Operación de tienda | [WCS-119](https://julioperezdev.atlassian.net/browse/WCS-119) | Catálogo, variantes, stock, media S3 y bandeja de atención humana | Implementación backend/frontend/IaC; pendiente smoke de bucket y UI |
| Plataforma de agentes | [WCS-120](https://julioperezdev.atlassian.net/browse/WCS-120) | Registry, authoring, versiones, evaluación, fallback, ejecuciones y métricas | Implementado; falta smoke operativo post-merge y habilitación controlada |
| Configuración dinámica | [WCS-121](https://julioperezdev.atlassian.net/browse/WCS-121) | Feature flags de negocio con AppConfig sin reinicio | Runtime, publicación, rollback y UI protegida implementados; falta validar AppConfig desplegado |
| Venta asistida | [WCS-122](https://julioperezdev.atlassian.net/browse/WCS-122) | Pedidos idempotentes, compra conversacional y links de pago con Mercado Pago Sandbox | Implementación vertical en curso; provider mock por defecto |

El runtime conversacional actual permanece como fallback durante toda la
migración. El backoffice nunca será una dependencia necesaria para procesar un
mensaje entrante.

## Orden vigente de implementación

La cola de desarrollo se decide por el primer gate incompleto, no por el número
del ticket:

1. Ejecutar el smoke operativo de WCS-128, WCS-120 y WCS-121 después del
   despliegue: login/refresh/logout, casos `401/403`, authoring/lifecycle,
   activación protegida y publicación/rollback de flags.
2. Verificar WCS-121 para selección de agente/versión, kill switch, canary y
   rollback sin reinicio, con permisos productivos explícitos.
3. Validar trazas reales por ejecución y gates de evaluación/promoción.
4. Mantener WCS-122 aislado y en `mock` hasta completar migración, seguridad y
   pruebas; recién después activar Mercado Pago Sandbox.

WCS-122 puede desarrollarse en paralelo porque no modifica el runtime de
agentes ni habilita pagos reales. Su entrada a producción requiere los gates
anteriores y un smoke explícito del proveedor.

## Arquitectura

```text
React/TypeScript
      |
      v
Backoffice API Spring Boot
      |
  +---+-----------+-------------+-------------+
  |               |             |             |
Catálogo       Handoff       Agents       Feature flags
PostgreSQL     PostgreSQL    PostgreSQL   AppConfig
  |               |             |             |
S3 presigned     contexto     métricas      snapshot dinámico
uploads          sanitizado   y auditoría  + rollback
```

El navegador no accede directamente a PostgreSQL, S3, AppConfig ni Mercado
Pago. Todas las operaciones pasan por APIs autorizadas, auditables e
idempotentes.

## Capacidades del MVP

### Operación de tienda

- productos, variantes, SKU, precio, moneda, talle, color y stock;
- filtros, paginación y estado activo;
- ajuste de stock con validación de no negativos, actor, motivo e idempotencia;
- carga de imágenes mediante URL prefirmada de S3 y persistencia de la key;
- no se almacenan datos de tarjeta ni se exponen credenciales del proveedor.

### Atención humana

- cola paginada por prioridad, estado y vencimiento;
- ownership para tomar y liberar una tarea;
- detalle con resumen y contexto mínimo sanitizado;
- acciones de asignar, tomar, resolver y devolver a cola;
- el contexto no muestra conversaciones completas ni PII innecesaria por
  defecto.

### Plataforma de agentes

El mapa representa explícitamente:

```text
caso de uso
  -> router
  -> agente especialista
  -> tool / Knowledge Base
  -> humanizer
  -> fallback o handoff
```

Cada nodo muestra agente, versión, estado, antigüedad, ejecuciones, latencia,
tokens, costo estimado, tasa de éxito y fallback. La simulación de una
desactivación debe mostrar la ruta alternativa antes de modificar nada.

### Estado actual de WCS-120

El corte implementado de WCS-120 entrega:

- registry de agentes, versiones, estados y activaciones sanitizadas;
- mapa de casos de uso y relaciones de fallback;
- simulación de desactivación sin mutar activaciones ni feature flags;
- runs de evaluación, detalle, comparación y métricas de tokens, costo,
  latencia y resultados;
- preflight de activación sin persistencia.

Además, el authoring permite seleccionar una versión registrada, cargar su
metadata sanitizada, editarla y guardar una nueva `DRAFT`. La versión anterior
permanece inmutable. Los selectores de activación y preflight se alimentan de
las asignaciones reales del registry (`agentId`, ambiente, canal, caso de uso y
versión), evitando activar rutas con valores escritos de memoria.

Las ejecuciones productivas se persisten en `agent_execution_traces`, mientras
que los runs de evaluación se mantienen como evidencia separada. La simulación
produce `FALLBACK_AGENT`, `HUMAN_REQUIRED` o `NO_CHANGE` y no persiste cambios.

El cierre operativo de WCS-120 requiere validar en el ambiente objetivo:

- login Cognito y permisos `agent-registry.read`, `agent-registry.write` y
  `agent-registry.publish`;
- creación/edición por nueva versión, lifecycle, activación, kill switch y
  rollback con evidencia de auditoría;
- trazas runtime y evaluación de la ruta afectada;
- rollback de flags y runtime sin destruir historial ni migraciones.

El panel debe mostrar explícitamente si una operación es `READ_ONLY`, `PREVIEW`,
`DISABLED` o `AVAILABLE`; una simulación o un preflight nunca equivale a una
activación real.

### Feature flags

AppConfig administra sólo comportamientos de negocio: selección de agente,
versión activa, kill switch, canary, Knowledge Base, límites de respuesta y
políticas de handoff. No administra secretos ni propiedades bootstrap.

El refresco dinámico debe mantener un snapshot válido y actualizarlo de forma
atómica:

1. obtiene la última configuración con AppConfig Data API;
2. valida schema y allowlist;
3. rechaza secretos o valores inválidos;
4. publica el snapshot inmutable en memoria;
5. registra versión, actor lógico, resultado y timestamp;
6. conserva la última versión válida para rollback.

## Seguridad y trazabilidad

Los roles iniciales son `store-viewer`, `store-operator`, `agent-operator` y
`admin`. La lectura y la escritura se autorizan por capacidades separadas.

Las métricas y eventos deben incluir, sin contenido sensible:

```text
requestId, conversationId pseudonimizado, channel, useCase,
agentId, agentVersion, modelProvider, modelId, tool, outcome,
latencyMs, inputTokens, outputTokens, totalTokens, estimatedCostUsd,
fallbackReason, featureFlagVersion
```

No se registran prompts completos, secretos, tokens ni conversaciones completas
en logs operativos.

## Pedidos y Mercado Pago

El slice de venta asistida usará Sandbox y deberá incluir pedido idempotente,
compra conversacional con selección inequívoca, preferencia/link de pago,
webhook firmado y deduplicado, estados de pago y trazabilidad. No se capturan
ni almacenan datos de tarjeta. El contrato completo está en
[`conversational-checkout.md`](conversational-checkout.md).

## Criterios de cierre del backoffice MVP

- catálogo, stock e imágenes operables desde el panel;
- solicitudes humanas visibles con contexto mínimo y ownership;
- panel read-only de agentes y casos de uso consultable;
- métricas de evaluación por agente y versión disponibles, identificadas como
  evidencia de evaluación mientras no exista telemetría runtime completa;
- feature flag modificable sin restart y con rollback;
- pedido idempotente y trazable, con link mock o Sandbox según configuración,
  también disponible desde el chat sólo ante una compra explícita;
- pruebas automatizadas, smoke documentado y rollback verificado;
- ausencia de secretos y PII innecesaria en UI, logs y evidencias.

El cierre específico de WCS-120 exige además todos los puntos de “Estado actual
de WCS-120”; WCS-122 no cambia ese criterio ni habilita por sí solo pagos
productivos.

## Rollback

1. volver el feature flag a la versión aprobada anterior;
2. desactivar el módulo de backoffice sin afectar webhooks ni runtime;
3. volver al baseline documentado sin eliminar migraciones aplicadas ni destruir
   recursos.
