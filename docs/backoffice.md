# Backoffice interno de evaluaciones

El directorio [`backoffice/`](../backoffice/) contiene el primer panel React +
TypeScript de WCS. El panel técnico mantiene consultas read-only de runs,
comparación de evidencia, registry y preflight. El slice operativo agrega
consulta de catálogo, ajustes de stock auditados y acciones acotadas sobre la
bandeja de atención humana; no publica agentes, no cambia feature flags, no
activa versiones y no ejecuta evaluaciones.

La evolución hacia el backoffice operativo de tienda está definida en
[`backoffice-mvp-roadmap.md`](backoffice-mvp-roadmap.md) y se implementa en los
slices `WCS-119` a `WCS-122`. Este documento conserva el contrato del primer
panel técnico y sus garantías de seguridad.

## Operación de tienda — WCS-119

La primera entrega operativa expone, cuando
`wcs.backoffice.enabled=true` y `wcs.backoffice.local-mode-enabled=true`, los
siguientes endpoints internos:

| Endpoint | Propósito |
| --- | --- |
| `GET /internal/backoffice/catalog` | Consulta paginada de productos y variantes |
| `POST /internal/backoffice/catalog/variants/{sku}/stock` | Ajuste atómico de stock con `Idempotency-Key` y auditoría |
| `GET /internal/backoffice/human-follow-ups` | Bandeja con contexto mínimo sanitizado |
| `POST /internal/backoffice/human-follow-ups/{id}/claim` | Toma exclusiva por `X-WCS-Actor-Key` |
| `POST /internal/backoffice/human-follow-ups/{id}/release` | Devuelve la tarea a la cola |
| `POST /internal/backoffice/human-follow-ups/{id}/resolve` | Marca la tarea como resuelta |
| `POST /internal/backoffice/catalog/products/{id}/image/upload-url` | Solicita URL prefirmada de S3 |
| `POST /internal/backoffice/catalog/products/{id}/image/confirm` | Persiste la key después del upload |

El ajuste de stock usa lock pesimista, rechaza resultados negativos y guarda
actor, motivo, delta y clave idempotente en
`wcs.catalog_stock_adjustments`. Las acciones de atención humana sólo permiten
la transición válida del owner. Las imágenes admitidas son JPEG, PNG y WebP,
con máximo de 5 MB y URL válida durante 10 minutos. S3 permanece deshabilitado
hasta configurar bucket y rol.

Para un smoke local controlado, iniciar el backend con el perfil `local` y las
dos propiedades de backoffice habilitadas; luego ejecutar `npm run dev` dentro
de `backoffice`. El proxy de Vite redirige `/internal` a `localhost:8080`; no
usar secretos ni la base productiva para esta prueba. El perfil evita que el
modo local pueda habilitarse accidentalmente en `prod`.

## Seguridad

- El backend sigue siendo la autoridad de autorización y mantiene el control
  plane cerrado por defecto.
- El panel no incluye secretos en el código ni en el build.
- El token se ingresa sólo en memoria de la sesión del navegador para pruebas
  internas; no se persiste en `localStorage`, archivos ni logs.
- No se muestran prompts completos, conversaciones, PII, SQL ni secretos.
- El registry muestra sólo metadata: estado, modelo, límites, allowlists,
  versión/hash de prompt y activaciones; nunca contenido de prompts ni actores.
- El preflight usa el scope `agent-registry.read`; no necesita ni acepta el
  scope de escritura y no reclama `Idempotency-Key`.
- Un `401` o `403` es un resultado operativo esperado cuando JWT no está
  habilitado o el scope no es suficiente.

## Ejecución local

```bash
cd backoffice
npm install
npm run dev
```

La URL del control plane se puede cambiar con la variable no sensible
`VITE_WCS_CONTROL_PLANE_BASE_URL`. Por defecto es
`/internal/agent-evaluations`, lo que permite servir el panel detrás del mismo
origen cuando exista un proxy autenticado.

El endpoint del registry se configura de forma independiente con
`VITE_WCS_AGENT_REGISTRY_BASE_URL`; por defecto es
`/internal/agent-registry`.

```bash
VITE_WCS_CONTROL_PLANE_BASE_URL=http://localhost:8080/internal/agent-evaluations npm run dev
```

El panel no habilita JWT en el backend. Para una prueba autorizada se ingresa
un token temporal en el campo de sesión; no se agrega un token al `.env`, al
repositorio ni al pipeline.

## Pipeline

`.github/workflows/frontend.yml` ejecuta `npm ci`, tests y build sólo cuando
cambia `backoffice/**` o el workflow. No tiene permisos AWS y no publica el
artefacto. El backend mantiene su pipeline y su aprobación de producción
separados.

## Contrato consumido

El cliente usa únicamente:

- `GET /internal/agent-evaluations/runs`;
- `GET /internal/agent-evaluations/runs/{runId}`;
- `GET /internal/agent-evaluations/comparisons`.
- `GET /internal/agent-registry/agents` con filtros opcionales `agentId`,
  `environment`, `channel`, `useCase` y un `limit` máximo de 100.
- `POST /internal/agent-registry/activations/preflight`, que valida una
  solicitud completa sin persistir claims ni activaciones.

La lista puede mostrar `totalTokens`, `providerLatencyMs` y
`estimatedCostUsd` cuando todas las ejecuciones del run tienen esos datos. Si
el proveedor no los devuelve, el valor aparece como desconocido (`—`), nunca
como cero inventado.

El endpoint del registry requiere el scope independiente
`agent-registry.read`; el scope `agent-evaluation.read` no lo habilita. La
seguridad sigue deshabilitada por defecto hasta conectar un IdP aprobado. La
respuesta es una lista acotada de agentes con versiones y activaciones, y no
ofrece operaciones de escritura. El preflight responde `READY`, `BLOCKED` o
`DENIED` y detalla checks sanitizados; `READY` no equivale a una activación y
requiere todavía el control plane write-only y sus aprobaciones.

## Operación del preflight

El preflight debe usarse antes de cualquier activación. Comprueba que la
versión exista, esté `APPROVED`, tenga referencias de aprobación y cumpla la
misma política de rollout que el comando de activación. No escribe PostgreSQL,
no modifica AppConfig y no ejecuta Bedrock.

La ruta de escritura permanece cerrada por
`wcs.agent-registry.activation-write-enabled=false`; el runtime también sigue
cerrado por `wcs.agent-runtime.activation-enabled=false`.

## Rollback

Eliminar o deshabilitar `backoffice/` y su workflow. El backend, los webhooks,
los canales y la infraestructura no dependen de este panel.
