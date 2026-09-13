# Backoffice interno de evaluaciones

El directorio [`backoffice/`](../backoffice/) contiene el primer panel React +
TypeScript de WCS. El panel técnico mantiene consultas read-only de runs,
comparación de evidencia, registry y preflight. El slice operativo agrega
consulta de catálogo, ajustes de stock auditados y acciones acotadas sobre la
bandeja de atención humana. La consola de activaciones agrega promoción,
rollback y kill switch, pero permanece cerrada por defecto y separada del
runtime conversacional.

WCS-121 agrega un panel separado para consultar el snapshot de feature flags,
ver su versión efectiva/stale/auditoría y preparar publicación o rollback. La
escritura requiere el scope `feature-flags.write` y el backend sigue siendo la
autoridad de validación y autorización.

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

## Mapa de agentes — WCS-120

El panel incorpora una proyección read-only del mapa de ejecución y una
simulación de desactivación. Los endpoints están protegidos por la capacidad
`agent-registry.read` y no mutan activaciones, AppConfig ni PostgreSQL:

| Endpoint | Propósito |
| --- | --- |
| `GET /internal/backoffice/agent-map` | Casos de uso, agentes, versiones, tools, Knowledge Bases, fallback y métricas agregadas |
| `POST /internal/backoffice/agent-map/simulations` | Simula desactivar un agente y devuelve fallback compatible, handoff humano o ausencia de cambio |

Los filtros son `environment`, `channel`, `useCase` y `agentId`. La respuesta
agrupa cada caso de uso y expone relaciones `ROUTES_TO`, `USES_TOOL`,
`USES_KNOWLEDGE_SOURCE`, `FALLBACK_TO` y `HANDOFF_TO_HUMAN`. La simulación es
explícita y no equivale a un kill switch real.

En esta primera versión, `executionCount`, latencia, tokens, costo y tasa de
éxito se calculan sobre los runs de evaluación sanitizados persistidos por
WCS-61. La respuesta declara `metricsSource=EVALUATION_RUNS` y el contador de
runs inspeccionados; no debe interpretarse como tráfico productivo. Si la
historia supera la página acotada de 100 runs, `evidenceTruncated=true`. La
telemetría de runtime productivo se incorporará cuando exista un store de
trazas de ejecución.

Si el agente activo no tiene fallback compatible, la simulación devuelve
`HUMAN_REQUIRED`. El mapa no promueve una versión ni cambia el estado de
ningún agente; las mutaciones sólo están disponibles en la consola de
activaciones descrita más abajo.

## Control de activaciones — WCS-120

La consola permite operar una versión ya aprobada después de ejecutar un
preflight con la misma solicitud. La activación queda bloqueada en la UI si el
preflight está ausente, desactualizado o no responde `READY`. Rollback y kill
switch requieren referencias de aprobación, pero no necesitan un nuevo
preflight porque operan sobre la activación vigente.

| Acción | Endpoint | Resultado |
| --- | --- | --- |
| Activar versión | `POST /internal/agent-registry/activations` | Crea una referencia de activación para `agentId + version` |
| Kill switch | `POST /internal/agent-registry/activations/kill-switch` | Deshabilita la activación vigente sin borrar historial |
| Rollback | `POST /internal/agent-registry/activations/rollback` | Crea una nueva referencia hacia la versión anterior |

Las tres acciones envían `Idempotency-Key`. El backend exige además el scope
`agent-registry.write`, la flag
`wcs.agent-registry.activation-write-enabled=true` y las aprobaciones
correspondientes. El cliente genera una clave nueva por acción; si una orden
recibe `ALREADY_PROCESSED`, no se reintenta automáticamente. Las respuestas
son sanitizadas y sólo incluyen estado, motivo y referencia de la activación.

La interfaz refresca el registry después de una mutación exitosa. No habilita
el runtime ni cambia AppConfig: la activación del tráfico continúa siendo un
gate operativo independiente (`wcs.agent-runtime.activation-enabled`).

### Authoring y lifecycle protegido — WCS-120

La siguiente entrega incorpora una superficie de escritura separada del panel
read-only. Permite registrar metadata de una nueva versión, clonar una versión
existente y avanzar su lifecycle de forma secuencial:

`DRAFT -> CANDIDATE -> EVALUATED -> APPROVED`

Los comandos requieren un `Idempotency-Key`, una identidad autenticada con la
capacidad `agent-registry.write` y la propiedad
`wcs.agent-registry.authoring-write-enabled=true`. Ambas condiciones deben
cumplirse; la propiedad permanece `false` por defecto. El endpoint no acepta
prompts, schemas ni secretos: sólo referencias de versión y hashes SHA-256 de
artefactos externos.

| Endpoint | Propósito |
| --- | --- |
| `POST /internal/agent-registry/agents/{agentId}/versions` | Crea una versión `DRAFT` a partir de metadata validada |
| `POST /internal/agent-registry/agents/{agentId}/versions/{version}/clone` | Clona metadata en una nueva versión `DRAFT` |
| `POST /internal/agent-registry/agents/{agentId}/versions/{version}/lifecycle` | Avanza a `CANDIDATE`, `EVALUATED` o `APPROVED` |

La aprobación exige referencia de evaluación técnica y referencia de aprobación
operativa. La definición persistida es inmutable; sólo se actualizan los
campos de lifecycle y aprobación mediante una actualización optimista que
comprueba el estado anterior. Las claves idempotentes se almacenan como hash
en `wcs.agent_registry_command_claims` y no se guarda la clave original.

La pantalla de authoring es una primera interfaz técnica protegida. No activa
versiones en tráfico, no modifica AppConfig y no reemplaza todavía el flujo de
activación/preflight. La promoción a `ACTIVE` continuará siendo un comando
separado, con su propio permiso, auditoría y rollback.

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
- Las mutaciones usan el scope separado `agent-registry.write`, exigen
  `Idempotency-Key` y permanecen cerradas por la flag de backend. El formulario
  nunca muestra prompts, secretos, actores ni el contenido de las
  aprobaciones.
- Un `401` o `403` es un resultado operativo esperado cuando JWT no está
  habilitado o el scope no es suficiente.

### Conexión y actualización global

El panel ofrece la acción `Conectar y actualizar todo`. Se habilita únicamente
cuando el campo `Token de sesión (memoria)` tiene contenido. Al ejecutarla,
primero realiza la lectura read-only de `runs` para validar la URL, el token y
la autorización; sólo si esa lectura es exitosa solicita en paralelo registry,
mapa de agentes, operación de tienda y feature flags.

La acción informa si todas las áreas se actualizaron o si el resultado fue
parcial. Cada loader conserva su propio error y los botones individuales siguen
disponibles para reintentar una sección. Durante la actualización global se
deshabilitan los refresh read-only individuales para evitar solicitudes
duplicadas. El token permanece sólo en memoria, igual que antes.

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

### Backend desplegado desde el frontend local

Para probar el panel en `http://localhost:5173` contra el backend publicado en
AWS App Runner, crear `backoffice/.env.local` —este archivo está ignorado por
Git— con:

```dotenv
VITE_WCS_BACKEND_BASE_URL=https://guapajjmta.us-east-1.awsapprunner.com
```

El proxy de Vite enviará todas las rutas `/internal` al backend desplegado y
mantendrá el navegador en el mismo origen, evitando una configuración CORS
adicional para esta prueba local. Reiniciar Vite después de crear o cambiar el
archivo. El hostname no es un secreto; los tokens de sesión siguen siendo
temporales, se ingresan en memoria desde el panel y no deben guardarse en este
archivo.

Si `VITE_WCS_BACKEND_BASE_URL` no existe, el comportamiento vuelve a ser el
backend local en `http://localhost:8080`. Un `401` o `403` después del cambio
confirma que la solicitud llegó a App Runner y representa autorización del
backoffice, no un problema de conectividad.

El endpoint del registry se configura de forma independiente con
`VITE_WCS_AGENT_REGISTRY_BASE_URL`; por defecto es
`/internal/agent-registry`.

El mapa se configura con `VITE_WCS_AGENT_MAP_BASE_URL`; por defecto es
`/internal/backoffice/agent-map`.

```bash
VITE_WCS_CONTROL_PLANE_BASE_URL=http://localhost:8080/internal/agent-evaluations npm run dev
```

El panel no habilita JWT en el backend. Para una prueba autorizada se ingresa
un token temporal en el campo de sesión; no se agrega un token al `.env`, al
repositorio ni al pipeline.

### Preview remoto read-only del backend

El preview remoto permite consultar desde el frontend local la información ya
implementada en WCS contra App Runner, sin habilitar todavía un IdP ni exponer
operaciones de escritura. Terraform crea el secreto dedicado
`wcs/prod/backoffice` con un valor placeholder; antes de activar el preview hay
que reemplazarlo en AWS Secrets Manager por un token aleatorio fuerte con este
formato:

```json
{
  "preview-token": "<token-temporal-fuerte>"
}
```

En una instalación nueva, se publica `backoffice_preview_enabled=true` en el
`terraform.tfvars` del environment de producción y el baseline de AppConfig
incluye estas claves:

```text
wcs.backoffice.enabled=true
wcs.backoffice.preview.enabled=true
wcs.external-config.secrets-manager.backoffice-secret-id=wcs/prod/backoffice
```

En el stack existente, Terraform conserva por diseño el contenido y la
versión desplegada del profile hosted de AppConfig (`ignore_changes`). Por eso,
además de crear el secret y sus permisos, hay que agregar las tres claves al
profile `runtime`, publicar una nueva versión y reiniciar App Runner. No se debe
pegar el token en AppConfig: sólo se guarda la referencia al secret.

La aplicación carga el token desde Secrets Manager al arrancar. En el panel se
ingresa el mismo token en `Token de sesión (memoria)`. El preview permite sólo
lecturas de catálogo, bandeja de atención humana, evaluaciones, registry, mapa
de agentes y feature flags, además de los endpoints no mutantes de preflight y
simulación de rutas. Ajustes de stock, claims/releases/resolutions, uploads de
imágenes y publicaciones/rollbacks de flags responden `403`, incluso con el
token correcto.

Para rollback, volver `backoffice_preview_enabled=false`, aplicar el cambio y
reiniciar App Runner; como medida inmediata también se puede revocar o rotar el
valor del secreto. Esta modalidad es temporal: luego se reemplazará por un
cliente autenticado mediante IdP, sin cambiar los endpoints ni la autorización
de aplicación.

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
- `POST /internal/agent-registry/activations`, `/kill-switch` y `/rollback`,
  que ejecutan mutaciones idempotentes sólo con `agent-registry.write` y la
  flag de escritura habilitada.
- `GET /internal/backoffice/agent-map`, que proyecta el grafo sanitizado y
  métricas de evidencia.
- `POST /internal/backoffice/agent-map/simulations`, que calcula una ruta de
  fallback sin mutar activaciones.
- `GET /internal/backoffice/feature-flags`, que muestra el snapshot efectivo y
  auditoría sanitizada.
- `POST /internal/backoffice/feature-flags/publish` y `/rollback`, protegidos
  con `feature-flags.write` y cerrados por defecto.

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

La consola web refleja esas dos condiciones, pero no puede sustituir la
autorización del backend. Para una prueba local se debe usar un token de
desarrollo con los scopes adecuados y una base de datos de prueba; nunca se
deben reutilizar referencias ni credenciales de producción.

El mapa reutiliza la misma autorización de registry. Un actor ausente o sin
`agent-registry.read` recibe `403` y no recibe metadata del mapa.

## Rollback

Eliminar o deshabilitar `backoffice/` y su workflow. El backend, los webhooks,
los canales y la infraestructura no dependen de este panel.
