# WCS — Backoffice operativo MVP

Estado: `Accepted / In progress`  
Épica: [WCS-118](https://julioperezdev.atlassian.net/browse/WCS-118)

La primera entrega de implementación se concentra en WCS-119 para validar el
vertical completo con un único PR: consulta operativa, stock auditado, media
desacoplada y ownership de seguimientos. La autorización productiva, el mapa
de agentes y los feature flags de negocio permanecen en los slices siguientes.

Este documento define el MVP del backoffice operativo de WCS. La entrega se
organiza en cuatro slices grandes para reducir la fricción de múltiples PR
pequeños. Cada slice incluye backend, frontend, pruebas, observabilidad,
documentación, rollout y rollback.

## Slices de entrega

| Slice | Jira | Alcance |
| --- | --- | --- |
| Operación de tienda | [WCS-119](https://julioperezdev.atlassian.net/browse/WCS-119) | Catálogo, variantes, stock, media S3 y bandeja de atención humana |
| Plataforma de agentes | [WCS-120](https://julioperezdev.atlassian.net/browse/WCS-120) | Grafo de casos de uso, versiones, fallback, ejecuciones y métricas |
| Configuración dinámica | [WCS-121](https://julioperezdev.atlassian.net/browse/WCS-121) | Feature flags de negocio con AppConfig sin reinicio |
| Venta asistida | [WCS-122](https://julioperezdev.atlassian.net/browse/WCS-122) | Pedidos y links de pago con Mercado Pago Sandbox |

El runtime conversacional actual permanece como fallback durante toda la
migración. El backoffice nunca será una dependencia necesaria para procesar un
mensaje entrante.

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
preferencia/link de pago, webhook firmado y deduplicado, estados de pago y
trazabilidad. No se capturan ni almacenan datos de tarjeta.

## Criterios de cierre

- catálogo, stock e imágenes operables desde el panel;
- solicitudes humanas visibles con contexto mínimo y ownership;
- mapa de agentes y casos de uso consultable;
- métricas por agente y versión disponibles;
- feature flag modificable sin restart y con rollback;
- pago Sandbox idempotente y trazable;
- pruebas automatizadas, smoke documentado y rollback verificado;
- ausencia de secretos y PII innecesaria en UI, logs y evidencias.

## Rollback

1. volver el feature flag a la versión aprobada anterior;
2. desactivar el módulo de backoffice sin afectar webhooks ni runtime;
3. volver al baseline documentado sin eliminar migraciones aplicadas ni destruir
   recursos.
