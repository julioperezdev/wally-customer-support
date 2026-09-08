# ADR-022 — Política explícita de retención de evidencia de evaluación

- **Estado:** Accepted for implementation boundary; policy activation pending
- **Fecha:** 2026-09-08
- **Jira:** `WCS-65`
- **Ámbito:** bounded context `agent`, lifecycle de evidencia

## Contexto

WCS persiste evidencia agregada y sanitizada de evaluaciones para comparar
versiones y medir calidad. La existencia de una fecha de vencimiento no debe
convertirse automáticamente en borrado: la purga requiere autorización,
retención aprobada por ambiente, auditoría y un mecanismo operativo reversible.

## Decisión

Se agrega una política explícita con una duración positiva y una decisión
determinística por run. El vencimiento se calcula como:

`completedAt + completedRunRetention`

Antes del instante de vencimiento el run queda `ACTIVE`; en el instante de
vencimiento o después queda `EXPIRED`. La decisión sólo contiene el `runId`,
las fechas y el estado. No lee ni expone contenido de escenarios.

La recomendación inicial para revisión de Producto/Legal es retener 90 días
los runs completados. Es una propuesta de evaluación y no un default productivo:
ningún bean de configuración, job o workflow activa purgas con esta tarea.

## Límites

- No se borran, archivan ni modifican runs.
- No se agrega scheduler, endpoint, configuración de AppConfig, S3, Lambda,
  SQS ni infraestructura AWS.
- No se mezclan los resultados de evaluación con la retención de conversaciones
  o memoria de clientes.
- Los consumidores deben entregar el instante de evaluación; no se usa el reloj
  del sistema de forma implícita.

## Consecuencias

WCS-66 permite revisar una página histórica y obtener decisiones y contadores
sin mutar runs. WCS-67 agrega un gate de evidencia que distingue una aprobación
operativa de la autenticación técnica; un futuro job autenticado podrá consultar
esa revisión antes de proponer una acción de retención. La activación deberá definir por separado autorización,
auditoría, retención por ambiente, período de gracia, paginación, métricas,
reintentos y rollback.

WCS-68 deja la autorización técnica detrás de un contrato independiente del
proveedor. La capacidad exacta y el ambiente deben validarse antes de invocar un
adaptador futuro; cualquier ausencia, discrepancia o error termina en
denegación. La aprobación operativa de WCS-67 nunca sustituye este control.

WCS-69 conecta esa frontera con la ejecución interna de una evaluación. La
idempotency key se reclama sólo después de una autorización exitosa y un
resultado previamente reclamado no vuelve a ejecutar el run. El guard de
idempotencia queda detrás de un port y debe ofrecer atomicidad en el adapter
que lo implemente; esta tarea no selecciona almacenamiento ni expone un
trigger remoto.

WCS-70 implementa el adapter PostgreSQL con una tabla de claims de hash
SHA-256, restricción única e inserción atómica. Sólo se conserva el digest y el
timestamp de claim; la key cruda no se almacena. La tabla no tiene purga
automática ni cambia la política de retención de runs.
