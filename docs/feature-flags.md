# Feature flags de negocio — WCS-121

Owner: Tech Lead  
Status: `In Progress`  
Related Jira: `WCS-121`

## Frontera

Los flags de este documento controlan comportamientos de negocio y activación
de agentes. No contienen secretos, URLs, credenciales, prompts completos ni
propiedades bootstrap de Spring. La configuración técnica continúa en el
perfil AppConfig `runtime` y Secrets Manager.

El perfil separado `feature-flags` se consulta mediante AppConfig Data API.
WCS conserva un snapshot inmutable y reemplaza la referencia completa sólo
después de validar el documento. Un refresh inválido conserva la última versión
válida; un error prolongado marca el snapshot como `stale` y el runtime aplica
fallback seguro.

## Contrato

```json
{
  "schemaVersion": "1",
  "version": "wcs-121-initial",
  "flags": [
    {
      "key": "wcs.agent.catalog-specialist.enabled",
      "enabled": true,
      "killSwitch": false,
      "environments": ["prod"],
      "channels": ["telegram"],
      "useCases": ["catalog-search"],
      "agentIds": ["catalog-specialist"],
      "agentVersions": [1]
    }
  ]
}
```

Las dimensiones vacías significan todos los valores. Las keys deben pertenecer
a los namespaces allow-listed `wcs.agent`, `wcs.catalog`,
`wcs.conversation`, `wcs.human-handoff`, `wcs.memory` o `wcs.rag`. Se rechazan
campos cuyo nombre parezca secreto (`token`, `password`, `secret`,
`credential`, `authorization`, etc.).

La ausencia de `wcs.agent.<id>.enabled` no desactiva una activación registrada;
la flag sólo puede agregar un kill switch o restricción explícita. Las flags
de negocio consultadas por una feature concreta usan default `false` cuando no
existe una definición aplicable.

## API protegida

| Método | Endpoint | Scope | Propósito |
| --- | --- | --- | --- |
| `GET` | `/internal/backoffice/feature-flags` | `feature-flags.read` | Snapshot efectivo, estado stale y auditoría sanitizada |
| `POST` | `/internal/backoffice/feature-flags/publish` | `feature-flags.write` | Validar, crear hosted version y comenzar deployment |
| `POST` | `/internal/backoffice/feature-flags/rollback` | `feature-flags.write` | Publicar la última versión aprobada anterior |

El backend toma el actor del principal JWT; no acepta actor, credenciales ni
ambiente arbitrario desde el body. La escritura permanece cerrada si la
seguridad de control plane, `wcs.feature-flags.publisher.enabled` o el permiso
IAM opcional de AppConfig no están habilitados.

## Auditoría y observabilidad

Cada refresh, rechazo, publicación y rollback genera un evento estructurado
con operación, versión, resultado, actor sanitizado y motivo. El endpoint
expone sólo una ventana acotada de auditoría en memoria para operación rápida;
CloudWatch/Grafana es la fuente durable hasta incorporar un store de auditoría
dedicado. Nunca se registra el payload completo.

Eventos principales:

- `FEATURE_FLAGS_REFRESH_ACCEPTED`
- `FEATURE_FLAGS_REFRESH_REJECTED`
- `BACKOFFICE_FEATURE_FLAGS_PUBLISHED`
- `BACKOFFICE_FEATURE_FLAGS_ACCESS_DENIED`

## Rollout y rollback

1. Ejecutar preflight y publicar una versión pequeña, con dimensiones de
   ambiente/canal/caso de uso explícitas.
2. Verificar `effectiveVersion`, `stale=false` y los eventos de refresh.
3. Probar el kill switch sobre una activación sintética o un caso de uso no
   crítico.
4. Si la versión no cumple, ejecutar rollback desde el panel o publicar la
   versión anterior registrada. No editar Terraform para una operación de
   runtime; Terraform sólo conserva el bootstrap inicial y `ignore_changes`.

La capacidad de publicación en App Runner está deliberadamente desactivada
por defecto (`enable_appconfig_management=false`). Primero debe existir un
IdP/JWT y una revisión de permisos IAM con el recurso AppConfig acotado; hasta
entonces el endpoint sirve como contrato y puede probarse con un fake en tests.
