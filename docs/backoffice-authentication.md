# Autenticación Cognito del backoffice — WCS-127

## Objetivo

WCS-127 reemplaza el `preview-token` del panel por autenticación de usuarios
con Amazon Cognito y autorización por capacidades. La solución se mantiene
deshabilitada por defecto para que el despliegue actual siga funcionando hasta
completar el alta del User Pool, los usuarios, el smoke test y la actualización
de AppConfig.

La decisión de diseño es:

```text
Browser React -- Authorization Code + PKCE --> Cognito Hosted UI
     |                                             |
     | access token en memoria                     | grupos Cognito
     v                                             v
Backend Spring Resource Server -- grupos --> capacidades SCOPE_*
```

El navegador no recibe secretos de cliente, no guarda el access token en
`localStorage` y nunca accede directamente a PostgreSQL, S3, AppConfig,
Secrets Manager ni Mercado Pago.

## Roles y capacidades

Los grupos Cognito representan roles humanos. El backend los convierte a las
capacidades canónicas que ya usa la autorización de los endpoints:

| Grupo Cognito | Capacidades principales |
| --- | --- |
| `store-viewer` | Lectura de catálogo, pedidos, handoff, registry, evaluaciones y flags |
| `store-operator` | Catálogo y stock, lectura de pedidos, gestión de handoff |
| `order-operator` | Lectura de catálogo y pedidos; creación de pedidos y links de pago |
| `agent-operator` | Lectura/ejecución de evaluaciones, authoring de agentes y flags |
| `admin` | Todas las capacidades WCS |

Las capacidades de operación de tienda son:

```text
backoffice.catalog.read
backoffice.catalog.write
backoffice.catalog.media.write
backoffice.orders.read
backoffice.orders.write
backoffice.human-follow-up.read
backoffice.human-follow-up.write
```

Las capacidades del control plane de agentes son:

```text
agent-evaluation.read
agent-evaluation.execute
agent-registry.read
agent-registry.write
feature-flags.read
feature-flags.write
```

El Resource Server también publica scopes para clientes técnicos futuros, pero
el cliente público del navegador sólo solicita `openid`, `email` y `profile`.
No se entregan todos los scopes de negocio al browser: las capacidades se
derivan del grupo Cognito del usuario y se validan nuevamente en Spring
Security.

## Infraestructura Terraform

El módulo [`infra/modules/cognito-backoffice/`](../infra/modules/cognito-backoffice/)
crea:

- User Pool con email verificado, creación administrativa y política de
  contraseña fuerte.
- Resource Server `wcs-backoffice` y scopes versionados.
- App Client público sin secret, preparado para Authorization Code + PKCE.
- Grupos de roles y sus capacidades.
- Hosted UI Domain sólo cuando se define `cognito_domain_prefix`.

Variables importantes en `infra/environments/prod` y `test`:

```hcl
backoffice_cognito_enabled   = false
cognito_domain_prefix        = null
cognito_callback_urls        = ["http://localhost:5173/auth/callback"]
cognito_logout_urls          = ["http://localhost:5173/"]
cognito_enable_password_auth = false
cognito_deletion_protection  = "ACTIVE"
```

`backoffice_cognito_enabled=false` es el estado seguro inicial. Terraform no
crea usuarios ni contraseñas; esas operaciones deben hacerse con el flujo
administrativo de Cognito y quedar registradas en el runbook de operación.

## Rollout controlado

1. Ejecutar `terraform plan` en `test` y revisar que sólo aparezcan recursos
   Cognito y los permisos necesarios del role de Terraform. No aceptar ningún
   `destroy`, reemplazo inesperado o drift de cuenta/región.
2. Aplicar primero `test` después de aprobación explícita.
3. Definir un `cognito_domain_prefix` único y agregar la URL pública del
   backoffice a `cognito_callback_urls` y `cognito_logout_urls`.
4. Crear un usuario administrativo y agregarlo inicialmente a
   `store-viewer`; elevarlo a otro grupo sólo cuando el smoke test lo requiera.
5. Obtener los outputs `user_pool_id`, `client_id`, `issuer_uri` y
   `hosted_ui_domain`.
6. Configurar el frontend con variables no sensibles:

   ```dotenv
   VITE_WCS_COGNITO_ENABLED=true
   VITE_WCS_COGNITO_AUTHORITY=https://cognito-idp.us-east-1.amazonaws.com/<user-pool-id>
   VITE_WCS_COGNITO_CLIENT_ID=<app-client-id>
   VITE_WCS_COGNITO_REDIRECT_URI=http://localhost:5173/auth/callback
   VITE_WCS_COGNITO_LOGOUT_URI=http://localhost:5173/
   ```

7. Publicar en AppConfig, por ambiente, los valores no secretos:

   ```json
   {
     "wcs.agent-evaluation.control-plane.security.enabled": false,
     "wcs.agent-evaluation.control-plane.security.issuer-uri": "https://cognito-idp.us-east-1.amazonaws.com/<user-pool-id>",
     "wcs.agent-evaluation.control-plane.security.audience": "<app-client-id>",
     "wcs.backoffice.security.provider": "cognito",
     "wcs.backoffice.enabled": true
   }
   ```

   El flag de seguridad queda `false` hasta completar el smoke test. El
   `issuer-uri` y el `audience` pueden existir desde antes sin activar JWT.
8. Probar el login con PKCE, lectura con `store-viewer`, y confirmar que un
   `POST` de escritura devuelve `403` para ese grupo.
9. Agregar temporalmente `store-operator` u `order-operator`, probar sólo las
   acciones correspondientes y revisar los logs de autorización sin registrar
   tokens ni PII.
10. Habilitar
    `wcs.agent-evaluation.control-plane.security.enabled=true` mediante una
    versión de AppConfig aprobada y hacer smoke test de `/actuator/health`,
    lectura, escritura autorizada y escritura denegada.

El contenido de AppConfig existente tiene protección contra sobreescritura
automática. Por eso el apply de Terraform no debe asumirse como publicación
de una nueva versión de runtime: después del apply hay que publicar la versión
de AppConfig explícitamente y guardar su número como evidencia.

## Frontend

La implementación está en [`backoffice/src/cognito.ts`](../backoffice/src/cognito.ts)
y mantiene el token sólo en memoria de React. El callback valida `state`, usa
`code_verifier` de `sessionStorage` durante el intercambio PKCE y limpia la URL.
El ingreso manual con `preview-token` queda disponible sólo para transición y
lecturas internas; no habilita escrituras.

Archivo local ignorado recomendado: `backoffice/.env.local`. Para compartir
la forma de configuración sin valores reales, usar
[`backoffice/.env.example`](../backoffice/.env.example).

## Smoke test

Con el backend apuntando al entorno habilitado:

1. Abrir el backoffice y completar `Ingresar con Cognito`.
2. Verificar que el callback vuelve al panel sin mostrar el `code` en la URL.
3. Como `store-viewer`, confirmar `GET /internal/backoffice/catalog` y
   `GET /internal/backoffice/orders` con `200`, y un ajuste de stock o creación
   de pedido con `403`.
4. Cambiar el usuario a `store-operator` y confirmar la operación de catálogo
   prevista, con `Idempotency-Key` cuando corresponda.
5. Confirmar que un token expirado produce `401` y un token válido sin la
   capacidad produce `403`.
6. Verificar que no aparecen access tokens, códigos OAuth, contraseñas,
   prompts ni conversaciones en logs.

## Fuera de alcance de WCS-127

- Alta de usuarios desde Terraform o desde el panel.
- MFA, passkeys, federación empresarial y multi-tenant avanzado.
- Habilitación automática en producción.
- Persistencia de tokens en el navegador.
- Reemplazo del backend como autoridad de permisos.

La ampliación de roles, MFA o gestión de usuarios debe ser un issue separado
con revisión de seguridad y criterios de recuperación.
