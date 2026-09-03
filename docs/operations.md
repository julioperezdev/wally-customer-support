# Operación y configuración — WCS

Owner: Tech Lead  
Status: `Accepted`
Last reviewed: 2026-08-30  
Related Jira: `WCS-13`, `WCS-21`, `WCS-22`  
Related repository paths: `src/main/resources`, `.github/workflows`, `infra/`

## Ambientes

Por el momento WCS opera únicamente con el environment `prod` de AppConfig. La
aplicación de AppConfig es siempre `wally-customer-support` y el profile hosted
es siempre `runtime`; ambos identificadores están versionados en el bootstrap.

- `prod`: cuenta Meta, secrets gestionados y monitoreo obligatorio.
- `test`: perfil interno de pruebas con datos sintéticos y dobles de los adapters;
  no representa un ambiente operativo.

El Environment `production` de GitHub Actions es un gate de despliegue y no
debe confundirse con el environment `prod` de Spring/AppConfig.

## CI/CD objetivo

```text
Pull request
  → format / compile
  → unit + integration tests
  → contract tests
  → security/static checks

main
  → build image
  → publish artifact
  → deploy staging/production según aprobación
  → health/readiness
  → smoke E2E
  → evidencia en Jira
```

No se debe declarar un release operativo sólo por tener el PR verde.

## Base de infraestructura y pipelines

La base versionada en el repositorio está organizada así:

```text
infra/bootstrap/                  límites y verificaciones del state
infra/environments/prod/          composición del entorno WCS
infra/modules/appconfig/          application, environment y profile
infra/modules/runtime-secrets/    contenedor de Secrets Manager sin valores
infra/modules/bedrock-knowledge-base/  S3, S3 Vectors, Bedrock KB y service role
infra/modules/backend-apprunner/  ECR, IAM, App Runner opcional
infra/modules/github-backend-deploy/  OIDC y permisos de despliegue
ci/backend-deploy.sh              despliegue por digest y health-check
.github/workflows/backend.yml     verify; deploy manual
.github/workflows/terraform.yml   validate; plan/apply manual
knowledge-base/wcs/                documentos Markdown versionados para la KB
```

La validación de Terraform corre en pull requests y en cambios a `main` sin
acceder al backend remoto. El plan y el apply se ejecutan desde
`workflow_dispatch`: el plan escribe un resumen sin valores sensibles en el
resumen del workflow y el apply sólo puede ejecutarse desde `main`, con
`confirm_apply=true`, un rol AWS con OIDC y la aprobación del Environment
`production`. El workflow bloquea destrucciones y reemplazos, y aplica el plan
generado en esa misma ejecución. El deploy de backend también es manual y usa
una imagen identificada por digest.

Para habilitar Terraform en GitHub se deben configurar en el Environment
`production`:

- `AWS_TERRAFORM_ROLE_ARN`: ARN de un rol preaprobado con trust OIDC para
  `julioperezdev/wally-customer-support` y el subject del Environment
  `production`. GitHub puede usar el formato inmutable
  `repo:owner@owner_id/repository@repository_id:environment:production`; el
  trust policy de WCS contempla ambos formatos y los IDs deben coincidir con
  el repositorio real.
- `TERRAFORM_VARS`: archivo HCL con variables revisadas y referencias de ARN,
  nunca passwords, tokens, claves privadas ni otros valores secretos.
- una regla de aprobación con al menos un reviewer requerido para el
  Environment `production`.

El workflow no crea ni amplía automáticamente el rol AWS de Terraform. La
confianza OIDC y sus permisos deben revisarse en AWS, incluyendo el acceso a
la key de state de WCS y sólo los recursos que este stack administra. El apply
no se dispara por un push: primero se ejecuta `plan`, se revisa su resumen y
luego se vuelve a lanzar el workflow con `action=apply` y
`confirm_apply=true`.

La base de datos existente se configura como `shared_rds_*` y el runtime recibe
referencias a AppConfig/Secrets Manager. La carga efectiva de esos valores en
Spring sigue siendo el alcance de WCS-22; por eso App Runner queda desactivado
por defecto y esta base no constituye un deployment productivo.

El CI de Terraform usa un rol IAM dedicado de WCS, no el rol de `tesis-dev`.
Su trust OIDC está restringido al repositorio WCS y al Environment `production`;
su policy limita el state al prefijo de WCS y los permisos de servicio a los
recursos administrados por este stack. El primer apply del rol requiere un
bootstrap autorizado, porque un rol no puede autenticarse antes de existir.

## Configuración productiva

La configuración productiva se divide deliberadamente:

### AWS AppConfig — configuración no sensible

```text
wcs.whatsapp.graph-api-version
wcs.whatsapp.graph-api-base-url
wcs.whatsapp.phone-number-id
wcs.whatsapp.business-account-id
wcs.whatsapp.connect-timeout
wcs.whatsapp.read-timeout
wcs.telegram.enabled
wcs.telegram.adapter
wcs.telegram.api-base-url
wcs.telegram.allowed-chat-id
wcs.telegram.connect-timeout
wcs.telegram.read-timeout
wcs.outbox.max-attempts
wcs.ai.provider
wcs.ai.model
wcs.ai.region
wcs.rag.provider
wcs.rag.max-results
wcs.rag.knowledge-base-id (cuando el adapter Bedrock KB esté habilitado)
```

### AWS Secrets Manager — secretos

```text
wcs/{environment}/whatsapp
  access-token
  verify-token
  app-secret

wcs/{environment}/telegram
  bot-token
  webhook-secret-token

wcs/{environment}/database
  username
  password
  jdbc-url (si no lo provee el runtime)

wcs/{environment}/providers
  api keys de proveedores externos, sólo si fueran necesarias
```

Bedrock debe autenticarse preferentemente con IAM Role del workload; no se crea una API key para Bedrock. Los valores de Secrets Manager no se pasan a Jira, Confluence, la base de datos ni los logs.

El runtime implementa `AwsExternalConfigurationEnvironmentPostProcessor` como fuente temprana de configuración de Spring. Primero carga un snapshot de AWS AppConfig Data API y luego resuelve únicamente campos allow-listed de los secretos referenciados en AWS Secrets Manager. El nombre de la aplicación y el environment `prod` están versionados en `application.properties`; la región AWS es temporalmente `us-east-1`. Las credenciales se resuelven mediante la cadena estándar del SDK. El starter agrega los valores como `PropertySource` en memoria antes del binding de Spring: no genera ni modifica un `application.properties` en runtime. Los valores nunca se escriben en `application.properties`, el repositorio ni los logs.

### Contrato del documento de AppConfig

El profile hosted usa JSON. Puede ser plano o anidado; el loader aplana objetos a
claves separadas por punto. AppConfig contiene configuración no sensible y
referencias a secretos, por ejemplo:

```json
{
  "wcs.whatsapp.graph-api-version": "v25.0",
  "wcs.whatsapp.graph-api-base-url": "https://graph.facebook.com",
  "wcs.external-config.secrets-manager.database-secret-id": "tesis-dev-prod/rds",
  "wcs.external-config.secrets-manager.whatsapp-secret-id": "wcs/prod/whatsapp",
  "wcs.external-config.secrets-manager.telegram-secret-id": "wcs/prod/telegram",
  "wcs.telegram.enabled": false,
  "wcs.telegram.adapter": "disabled",
  "wcs.telegram.api-base-url": "https://api.telegram.org",
  "wcs.ai.provider": "bedrock",
  "wcs.ai.model": "openai.gpt-oss-20b-1:0",
  "wcs.ai.region": "us-east-1",
  "wcs.rag.provider": "bedrock-kb",
  "wcs.rag.max-results": 5,
  "wcs.rag.knowledge-base-id": "REPLACE_ME_WCS_KNOWLEDGE_BASE_ID"
}
```

El proveedor `bedrock-kb` representa la Knowledge Base documental propia de
WCS. Su fuente inicial es S3 y su vector store objetivo es S3 Vectors con Titan
Text Embeddings V2 de 1024 dimensiones. El identificador de la Knowledge Base
es configuración no sensible y el service role sólo debe ser utilizado por
Bedrock para leer la fuente y el índice propios de WCS.

Las consultas de catálogo, stock, carrito y pedidos no deben resolverse con
`wcs.rag.provider`. Esas capacidades se implementan como tools/casos de uso
WCS y consultan sus fuentes transaccionales con autorización, ownership y
queries parametrizadas.

Las referencias admitidas actualmente son:

| Referencia AppConfig | Campos JSON allow-listed | Propiedades Spring resultantes |
| --- | --- | --- |
| `database-secret-id` | `jdbc-url`/`jdbc_url`/`url`, `username`/`user`, `password` | `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password` |
| `whatsapp-secret-id` | `access-token`, `verify-token`, `app-secret` y variantes snake/camel | `wcs.whatsapp.access-token`, `wcs.whatsapp.verify-token`, `wcs.whatsapp.app-secret` |
| `telegram-secret-id` | `bot-token`, `webhook-secret-token` y variantes snake/camel | `wcs.telegram.bot-token`, `wcs.telegram.webhook-secret-token` |
| `runtime-secret-id` | combinación explícita de los campos anteriores | propiedades correspondientes |

El `secret-id` legacy sólo se usa como referencia genérica cuando no hay
referencias dedicadas. Los campos desconocidos se ignoran deliberadamente.
Esto evita que un nuevo campo agregado a un secret se convierta de forma
accidental en una propiedad de Spring.

### Precedencia y modos de ejecución

La precedencia efectiva es: argumentos de línea de comandos y propiedades del
sistema, Secrets Manager resuelto, AppConfig y defaults versionados en
`application.properties`. Los valores de los secretos no son variables de
entorno productivas.

AppConfig y Secrets Manager están habilitados permanentemente en el runtime
normal y usan fail-fast: la aplicación no arranca con configuración productiva
incompleta. Los tests sobrescriben esas propiedades en
`src/test/resources/application-test.properties` y usan dobles sintéticos.

El loader crea clientes AWS temporalmente en `us-east-1` y usa la cadena estándar
de credenciales del SDK. Sólo se hace una lectura al arranque; el redeploy o
restart consume la última versión desplegada de AppConfig y permite rotar
secrets sin recompilar.

### IAM mínimo

El rol de runtime necesita `appconfigdata:StartConfigurationSession`,
`appconfigdata:GetLatestConfiguration` y
`secretsmanager:GetSecretValue` sobre los ARNs concretos de WCS y del RDS
compartido, más los ARNs declarados para cualquier secret adicional referenciado
por AppConfig (por ejemplo, WhatsApp o Telegram). No se permite
`secretsmanager:ListSecrets` ni se usan access keys embebidas. Terraform
prepara los permisos y deja los valores fuera de su estado; el documento de
AppConfig puede versionarse porque sólo contiene referencias no sensibles.

No se requieren variables de entorno de aplicación para el arranque normal. En
local, el SDK usa una sesión o perfil AWS configurado fuera del repositorio; en
AWS usa el rol IAM del workload. Los secretos siguen siendo exclusivos de
Secrets Manager.

El entorno prod incluye por defecto una configuración hosted y versiones
bootstrap falsas para `wcs/{environment}/database`,
`wcs/{environment}/whatsapp` y `wcs/{environment}/telegram`. Los valores se
deben reemplazar desde la consola
antes de habilitar App Runner. Las versiones iniciales de Terraform ignoran
cambios posteriores hechos en consola para no revertir una rotación manual;
Terraform no debe recibir valores reales.

### Knowledge Base documental de WCS

La Knowledge Base propia se administra mediante el módulo
`infra/modules/bedrock-knowledge-base`. El bucket fuente y el vector store son
recursos separados de cualquier KB histórica. Los documentos aprobados viven
en `knowledge-base/wcs/` y Terraform los publica bajo `documents/` con
versionado y cifrado SSE-S3.

La configuración objetivo es:

- Amazon Bedrock Knowledge Bases con una data source S3.
- Amazon S3 Vectors como vector store.
- `amazon.titan-embed-text-v2:0`, `FLOAT32`, 1024 dimensiones y distancia
  euclídea.
- IAM exclusivo para Bedrock con lectura de los documentos y acceso al índice
  de WCS.

Después del apply, iniciar y revisar la ingesta de manera explícita:

```bash
./scripts/start-wcs-knowledge-ingestion.sh \
  --knowledge-base-id "$(terraform -chdir=infra/environments/prod output -raw knowledge_base_id)" \
  --data-source-id "$(terraform -chdir=infra/environments/prod output -raw knowledge_base_data_source_id)" \
  --wait
```

La operación debe registrar en Jira el job de ingesta, cantidad indexada y
cantidad fallida. No se incluyen conversaciones reales, PII, secretos ni
contenido del catálogo dinámico en esta fuente.

## Observabilidad mínima

- correlation/request ID;
- estado y dirección del mensaje;
- duración LLM y Meta;
- intentos;
- error code sanitizado;
- volumen y costo agregado;
- health y readiness;
- alertas por fallos, latencia, backlog y costo.

No registrar texto completo, firmas, tokens, números de teléfono completos ni payloads de Meta en producción.

## Runbooks requeridos

- Webhook no recibe eventos.
- Firmas inválidas.
- Backlog de procesamiento.
- Meta devuelve error o rate limit.
- LLM no responde o responde contenido inválido.
- Reintentos duplican mensajes.
- Rotación de secrets.
- Rollback de aplicación.
- Borrado de conversaciones según política.

## Reglas de disponibilidad

- La aplicación normal arranca con AppConfig y Secrets Manager; si una
  integración productiva requerida no está disponible, falla explícitamente.
- Los tests pueden activar dobles de cada adapter de forma independiente.
- Un provider no configurado falla de forma explícita y controlada; no se hace fallback silencioso a producción con datos sintéticos.
- Health verifica proceso y dependencias esenciales; readiness declara qué integración está deshabilitada o degradada sin imprimir secretos.
- La rotación de Secrets Manager debe ser probada sin recompilar ni cambiar código.

## Registro del webhook de Telegram

Cuando el endpoint público ya esté disponible, registrar el webhook mediante el
helper que obtiene el bot token y el secret desde `wcs/prod/telegram`:

```bash
./scripts/register-telegram-webhook.sh \
  --url https://<host-publico>/webhook/telegram
```

La URL debe ser HTTPS y terminar exactamente en `/webhook/telegram`. Para una
prueba local se puede usar una URL HTTPS temporal de ngrok. El registro se debe
repetir si cambia la URL temporal; no incluir el token en el comando.
La implementación usa webhook; el long polling no forma parte del runtime de
WCS. Para una prueba local, se expone la aplicación con ngrok y se registra esa
URL temporal.
