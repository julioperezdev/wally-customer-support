# ADR-025 — JWT configurable para el control plane de evaluaciones

- Estado: Accepted
- Fecha: 2026-09-08
- Relacionado: `WCS-76`, `WCS-77`, `WCS-78`
- Supersede parcialmente: [`024-agent-evaluation-control-plane-read-api.md`](024-agent-evaluation-control-plane-read-api.md)

## Contexto

WCS ya expone una API read-only para consultar histórico, comparaciones y
evidencia de evaluaciones. La API tiene un port de autorización independiente,
pero no debe aceptar una identidad enviada libremente por un header cuando
comience a ser consumida por un backoffice o job. También es importante que la
seguridad de este control plane no interrumpa los webhooks públicos ni el
actuator del runtime conversacional.

## Decisión

Se agrega Spring Security Resource Server de forma condicional y limitada a
`/internal/agent-evaluations/**`.

| Configuración | Regla |
| --- | --- |
| `wcs.agent-evaluation.control-plane.security.enabled` | `false` por defecto; habilita la cadena JWT sólo después de un rollout aprobado |
| `wcs.agent-evaluation.control-plane.security.issuer-uri` | issuer desde el que se descubren metadata y claves públicas |
| `wcs.agent-evaluation.control-plane.security.audience` | audience obligatoria para evitar aceptar tokens destinados a otro servicio |
| Scope requerido | `agent-evaluation.read`, expuesto como `SCOPE_agent-evaluation.read` |
| Actor | `sub` del JWT validado, usado como `Principal` y entregado al port |

La cadena protegida devuelve `401` si falta autenticación y `403` si el token
no tiene el scope requerido. El servicio de aplicación conserva una segunda
frontera provider-neutral y deny-by-default: la autenticación técnica no
reemplaza la autorización de WCS. El controller ya no acepta
`X-WCS-Actor-Id`; toma la identidad del `Principal` validado.

Una segunda cadena permite las rutas restantes cuando la seguridad está
habilitada. Por lo tanto Telegram, WhatsApp, health y demás endpoints públicos
no quedan sujetos al JWT del backoffice. Cuando la flag está deshabilitada se
instala una cadena pública explícita para evitar el login generado por Spring
Security y preservar el fallback actual.

## Alcance y fuera de alcance

Este slice agrega dependencias, configuración, validación de issuer/audience,
tests sintéticos y documentación operativa. No provisiona Cognito, otro IdP,
IAM, WAF, CORS, clientes del backoffice, endpoint para ejecutar evaluaciones ni
permisos AWS. Tampoco cambia el modelo de datos ni la API read-only.

## Seguridad y privacidad

No se registra el JWT, sus claims completos ni el actor crudo. Los eventos del
controller conservan sólo operación, capacidad, resultado, razón y duración.
Los tokens de prueba se generan en memoria y no contienen secretos ni PII.

## Rollout y rollback

El valor inicial es `enabled=false`. Para habilitar la cadena se requiere un
issuer accesible, audience aprobada y un cliente con el scope exacto. La
configuración se publica en AppConfig y se reinicia App Runner; no se necesita
Terraform para este cambio. Se valida `200/401/403` y se confirma que los
webhooks continúan respondiendo. Para rollback se vuelve a publicar
`enabled=false` y se reinicia el servicio; no se borran datos ni migraciones.

## Consecuencias

El control plane queda listo para ser consumido de forma autenticada sin
acoplar la aplicación a Cognito u otro proveedor. Mientras no exista un IdP
aprobado, continúa cerrado por la autorización deny-by-default y la flag
deshabilitada. La futura autenticación de ejecución deberá tener una capacidad
separada y no reutilizar automáticamente `agent-evaluation.read`.
