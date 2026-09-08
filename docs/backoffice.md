# Backoffice interno de evaluaciones

El directorio [`backoffice/`](../backoffice/) contiene el primer panel React +
TypeScript de WCS. Esta entrega es exclusivamente read-only: consulta runs,
detalle, comparación de evidencia y el registry de agentes/activaciones
sanitizado. No publica agentes, no cambia feature flags y no ejecuta
evaluaciones.

## Seguridad

- El backend sigue siendo la autoridad de autorización y mantiene el control
  plane cerrado por defecto.
- El panel no incluye secretos en el código ni en el build.
- El token se ingresa sólo en memoria de la sesión del navegador para pruebas
  internas; no se persiste en `localStorage`, archivos ni logs.
- No se muestran prompts completos, conversaciones, PII, SQL ni secretos.
- El registry muestra sólo metadata: estado, modelo, límites, allowlists,
  versión/hash de prompt y activaciones; nunca contenido de prompts ni actores.
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

La lista puede mostrar `totalTokens`, `providerLatencyMs` y
`estimatedCostUsd` cuando todas las ejecuciones del run tienen esos datos. Si
el proveedor no los devuelve, el valor aparece como desconocido (`—`), nunca
como cero inventado.

El endpoint del registry requiere el scope independiente
`agent-registry.read`; el scope `agent-evaluation.read` no lo habilita. La
seguridad sigue deshabilitada por defecto hasta conectar un IdP aprobado. La
respuesta es una lista acotada de agentes con versiones y activaciones, y no
ofrece operaciones de escritura.

## Rollback

Eliminar o deshabilitar `backoffice/` y su workflow. El backend, los webhooks,
los canales y la infraestructura no dependen de este panel.
