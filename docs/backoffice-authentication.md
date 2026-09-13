# Autenticación del backoffice con Cognito — WCS-128

## Decisión

El backoffice usa un login propio dentro de React. El navegador envía usuario
y contraseña al backend; sólo el backend llama a Cognito con el AWS SDK. No se
usa Hosted UI, OAuth redirect ni PKCE para este flujo.

```text
React
  │ POST /internal/auth/login (credentials: include)
  ▼
Spring Boot ── InitiateAuth(USER_PASSWORD_AUTH) ──► Amazon Cognito
  │                         │
  │ HttpOnly access/refresh cookies ◄─────────────┘
  ▼
GET /internal/auth/me + endpoints del backoffice
```

Los tokens nunca se devuelven al frontend, no se guardan en `localStorage` y
no se escriben en logs. El backend sigue siendo la autoridad de autorización:
valida el JWT del access token y las capacidades derivadas de los grupos de
Cognito.

## Contrato HTTP

| Método | Endpoint | Resultado |
| --- | --- | --- |
| `POST` | `/internal/auth/login` | Valida credenciales y crea cookies de sesión |
| `POST` | `/internal/auth/refresh` | Renueva el access token usando la cookie refresh |
| `POST` | `/internal/auth/logout` | Revoca la sesión en Cognito y limpia cookies |
| `GET` | `/internal/auth/me` | Devuelve identidad, grupos y capacidades, nunca tokens |

Login:

```json
{
  "username": "operator@example.com",
  "password": "<secreto-no-compartir>"
}
```

Respuesta exitosa:

```json
{ "status": "AUTHENTICATED" }
```

La sesión se transporta con dos cookies `HttpOnly`, `Secure` cuando está
habilitada la configuración segura, `SameSite=Lax` por defecto y `Path=/`:

- `wcs_backoffice_access`: access token de corta duración.
- `wcs_backoffice_refresh`: refresh token con duración controlada.

El frontend usa `credentials: include` y sólo recibe el contrato sanitizado de
`/me`:

```json
{
  "subject": "cognito-subject",
  "username": "operator@example.com",
  "groups": ["store-viewer"],
  "capabilities": ["backoffice.catalog.read"]
}
```

Errores estables:

| HTTP | Código | Significado |
| ---: | --- | --- |
| `401` | `INVALID_CREDENTIALS` | Usuario o contraseña inválidos |
| `401` | `INVALID_SESSION` | Refresh ausente o inválido |
| `403` | `FORBIDDEN` | Sesión válida sin capacidad para el recurso |
| `428` | `COGNITO_CHALLENGE_REQUIRED` | Cognito exige un flujo adicional, por ejemplo cambio de contraseña |
| `503` | `BACKOFFICE_AUTH_DISABLED` / `AUTH_PROVIDER_UNAVAILABLE` | Feature apagada o Cognito no disponible |

## Componentes

El contrato de aplicación está detrás de un adapter para que Cognito no
contamine la lógica de negocio:

- `BackofficeAuthenticationService`: valida el estado de la feature y
  orquesta login, refresh y logout.
- `BackofficeIdentityProvider`: puerto que abstrae el proveedor de identidad.
- `CognitoBackofficeIdentityProvider`: adapter que usa
  `CognitoIdentityProviderClient`, `USER_PASSWORD_AUTH` y
  `REFRESH_TOKEN_AUTH`.
- `BackofficeAuthenticationController`: cookies y contrato HTTP.
- `BackofficeCookieBearerTokenResolver`: permite que Spring Resource Server
  valide la cookie access, manteniendo soporte para `Authorization: Bearer`
  en smoke tests técnicos.
- `BackofficeCorsConfiguration`: permite sólo los orígenes configurados y
  credenciales de navegador.

El frontend ya no contiene integración con Cognito ni recibe el client secret;
la única configuración del navegador es la URL del backend o del proxy de
Vite.

## Terraform e infraestructura

El módulo [`infra/modules/cognito-backoffice/`](../infra/modules/cognito-backoffice/)
crea el User Pool, Resource Server, scopes y grupos. El App Client queda sin
secret porque la autenticación ocurre entre API y Cognito. Hosted UI y dominio
son opcionales y no forman parte de este login.

Cuando `backoffice_cognito_enabled=true`, el módulo habilita
`ALLOW_USER_PASSWORD_AUTH` automáticamente. Los callbacks y el dominio pueden
quedar vacíos si no se usa Hosted UI.

En producción, el rollout de esta capacidad está versionado en
[`infra/environments/prod/rollout.tfvars`](../infra/environments/prod/rollout.tfvars).
El workflow pasa ese archivo después del baseline `TERRAFORM_VARS`, por lo que
`backoffice_cognito_enabled=true` prevalece sobre el valor histórico del
secreto sin exponer ni reemplazar el resto de la configuración. El archivo sólo
contiene controles no sensibles y requiere revisión por PR.

El role de Terraform separa los permisos Cognito en una policy administrada
propia (`<project>-<environment>-terraform-cognito-access`). Esto evita superar
el límite de 10.240 bytes de una policy inline y agrega una dependencia
explícita para que la policy esté adjunta antes de crear el User Pool. Esta
corrección responde al fallo del workflow que combinaba `CreateUserPool` con
una actualización inline rechazada.

## AppConfig

La configuración no sensible se publica por ambiente. El estado seguro inicial
es `false`:

```json
{
  "wcs.backoffice.auth.enabled": false,
  "wcs.backoffice.auth.secure-cookies": false,
  "wcs.backoffice.auth.same-site": "Lax",
  "wcs.backoffice.auth.cognito.region": "us-east-1",
  "wcs.backoffice.auth.cognito.client-id": "<terraform-output-client-id>",
  "wcs.agent-evaluation.control-plane.security.enabled": false
}
```

Para habilitar el flujo, primero deben existir el User Pool, el App Client, el
usuario administrativo y sus grupos. Luego se publica una nueva versión de
AppConfig con ambos flags de seguridad habilitados de forma coordinada:

```json
{
  "wcs.backoffice.auth.enabled": true,
  "wcs.backoffice.auth.secure-cookies": true,
  "wcs.backoffice.auth.same-site": "Lax",
  "wcs.backoffice.auth.cognito.region": "us-east-1",
  "wcs.backoffice.auth.cognito.client-id": "<terraform-output-client-id>",
  "wcs.agent-evaluation.control-plane.security.enabled": true,
  "wcs.agent-evaluation.control-plane.security.issuer-uri": "https://cognito-idp.us-east-1.amazonaws.com/<user-pool-id>",
  "wcs.agent-evaluation.control-plane.security.audience": "<terraform-output-client-id>"
}
```

Para frontend y API en orígenes distintos, agregar
`wcs.backoffice.auth.cors-allowed-origins` con una lista separada por comas y
usar `SameSite=None` junto con HTTPS. Para desarrollo local se recomienda el
proxy de Vite y `SameSite=Lax`.

## Usuario, grupos y capacidades

Los grupos Cognito representan roles humanos. El backend deriva las
capacidades canónicas y las valida en cada endpoint:

| Grupo | Capacidades principales |
| --- | --- |
| `store-viewer` | Lectura de catálogo, pedidos, handoff, registry, evaluaciones y flags |
| `store-operator` | Catálogo, stock y gestión de handoff |
| `order-operator` | Lectura y creación de pedidos/links de pago |
| `agent-operator` | Evaluaciones, registry de agentes y feature flags |
| `admin` | Todas las capacidades WCS |

Terraform no crea contraseñas ni usuarios. El alta inicial se hace de forma
administrativa en Cognito y debe quedar como evidencia operativa. MFA,
passkeys, federación y administración de usuarios desde el panel quedan fuera
de WCS-128.

### Usuario de smoke productivo

Se creó un usuario técnico de prueba en el User Pool
`wally-customer-support-prod-backoffice`:

| Dato | Valor |
| --- | --- |
| Username | `wcs.demo.admin@example.com` |
| Grupo | `admin` (sólo para el smoke inicial) |
| Credenciales | Secret Manager `wcs/prod/backoffice-cognito-bootstrap` |

La contraseña no se guarda en el repositorio, Jira, Confluence ni logs. Para
obtenerla durante una prueba controlada, con una identidad AWS autorizada:

```bash
aws secretsmanager get-secret-value \
  --secret-id wcs/prod/backoffice-cognito-bootstrap \
  --region us-east-1 \
  --query SecretString \
  --output text | jq .
```

Este usuario es sólo para validar el flujo y debe rotarse o eliminarse al
finalizar el smoke test. Para operación normal se recomienda un usuario por
persona y grupos con mínimo privilegio (`store-viewer`, `store-operator`,
`order-operator` o `agent-operator`).

## Smoke test local

Con el backend habilitado y el usuario creado, el frontend se levanta con:

```bash
cd backoffice
npm install
npm run dev
```

El proxy de Vite debe apuntar a la API. El flujo esperado es:

1. Abrir el panel y enviar el formulario de login.
2. Confirmar que la respuesta no contiene `accessToken` ni `refreshToken`.
3. Confirmar que `/internal/auth/me` devuelve la identidad sanitizada.
4. Leer catálogo y pedidos con `store-viewer`.
5. Confirmar `403` al ejecutar una operación sin capacidad.
6. Cerrar sesión y confirmar que `/me` vuelve a `401`.
7. Revisar logs sin contraseñas, tokens, prompts, conversaciones ni PII
   innecesaria.

Prueba directa del contrato, usando variables locales y nunca valores reales
en shell history compartido:

```bash
curl -i -c /tmp/wcs-cookies.txt \
  -H 'Content-Type: application/json' \
  -d '{"username":"<usuario>","password":"<password>"}' \
  https://<api>/internal/auth/login

curl -i -b /tmp/wcs-cookies.txt https://<api>/internal/auth/me
```

No ejecutar `terraform apply` sólo por crear el PR. El apply requiere revisar
el plan, confirmar cuenta/región y verificar que no haya `destroy` o reemplazos
inesperados.
