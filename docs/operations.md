# Operación y configuración — WCS

Owner: Tech Lead  
Status: `Accepted`
Last reviewed: 2026-08-30  
Related Jira: `WCS-13`, `WCS-21`, `WCS-22`  
Related repository paths: `src/main/resources`, `.github/workflows`, `infra/`

## Ambientes

- `local-mock`: sin Meta ni LLM real, con PostgreSQL local.
- `staging`: integraciones controladas y datos sintéticos.
- `production`: cuenta Meta, secrets gestionados y monitoreo obligatorio.

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
infra/modules/backend-apprunner/  ECR, IAM, App Runner opcional
infra/modules/github-backend-deploy/  OIDC y permisos de despliegue
ci/backend-deploy.sh              despliegue por digest y health-check
.github/workflows/backend.yml     verify; deploy manual
.github/workflows/terraform.yml   validate; plan/apply manual
```

La validación de Terraform corre en pull requests y en cambios a `main` sin
acceder al backend remoto. El plan y el apply requieren `workflow_dispatch`,
un rol AWS con OIDC y variables revisadas en `TERRAFORM_VARS`; el workflow
bloquea planes con destrucciones. El deploy de backend también es manual y usa
una imagen identificada por digest.

La base de datos existente se configura como `shared_rds_*` y el runtime recibe
referencias a AppConfig/Secrets Manager. La carga efectiva de esos valores en
Spring sigue siendo el alcance de WCS-22; por eso App Runner queda desactivado
por defecto y esta base no constituye un deployment productivo.

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
wcs.outbox.max-attempts
wcs.ai.provider
wcs.ai.model
wcs.rag.provider
wcs.rag.max-results
```

### AWS Secrets Manager — secretos

```text
wcs/{environment}/whatsapp
  access-token
  verify-token
  app-secret

wcs/{environment}/database
  username
  password
  jdbc-url (si no lo provee el runtime)

wcs/{environment}/providers
  api keys de proveedores externos, sólo si fueran necesarias
```

Bedrock debe autenticarse preferentemente con IAM Role del workload; no se crea una API key para Bedrock. Los valores de Secrets Manager no se pasan a Jira, Confluence, la base de datos ni los logs.

El runtime implementa `AwsExternalConfigurationEnvironmentPostProcessor` como fuente temprana de configuración de Spring. Primero carga un snapshot de AWS AppConfig Data API y luego resuelve únicamente campos allow-listed de los secretos referenciados en AWS Secrets Manager. Los valores nunca se escriben en `application.properties`, el repositorio ni los logs.

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
  "wcs.ai.provider": "bedrock",
  "wcs.ai.model": "amazon.nova-lite-v1"
}
```

Las referencias admitidas actualmente son:

| Referencia AppConfig | Campos JSON allow-listed | Propiedades Spring resultantes |
| --- | --- | --- |
| `database-secret-id` | `jdbc-url`/`jdbc_url`/`url`, `username`/`user`, `password` | `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password` |
| `whatsapp-secret-id` | `access-token`, `verify-token`, `app-secret` y variantes snake/camel | `wcs.whatsapp.access-token`, `wcs.whatsapp.verify-token`, `wcs.whatsapp.app-secret` |
| `runtime-secret-id` | combinación explícita de los campos anteriores | propiedades correspondientes |

El `secret-id` legacy sólo se usa como referencia genérica cuando no hay
referencias dedicadas. Los campos desconocidos se ignoran deliberadamente.
Esto evita que un nuevo campo agregado a un secret se convierta de forma
accidental en una propiedad de Spring.

### Precedencia y modos de ejecución

La precedencia efectiva es: argumentos de línea de comandos y propiedades del
sistema, variables de entorno de bootstrap, Secrets Manager resuelto,
AppConfig, y defaults versionados en `application.properties`. Los nombres de
AppConfig y Secrets Manager son bootstrap; los valores de los secretos no son
variables de entorno productivas.

`AWS_APPCONFIG_ENABLED` y `AWS_SECRETS_MANAGER_ENABLED` deben estar en `true`
en un runtime AWS. Ambos soportan `*_FAIL_FAST=true`: un ambiente productivo
no debe arrancar con configuración incompleta. En local o `local-mock` se
mantienen en `false`, por lo que se puede iniciar, testear y desarrollar sin
credenciales AWS.

El loader crea clientes AWS con la cadena estándar de credenciales y la región
`AWS_REGION`. Sólo se hace una lectura al arranque; el redeploy o restart
consume la última versión desplegada de AppConfig y permite rotar secrets sin
recompilar.

### IAM mínimo

El rol de runtime necesita `appconfigdata:StartConfigurationSession`,
`appconfigdata:GetLatestConfiguration` y
`secretsmanager:GetSecretValue` sobre los ARNs concretos de WCS y del RDS
compartido, más los ARNs declarados para cualquier secret adicional referenciado
por AppConfig (por ejemplo, WhatsApp). No se permite
`secretsmanager:ListSecrets` ni se usan access keys embebidas. Terraform
prepara los permisos y deja los valores fuera de su estado; el documento de
AppConfig puede versionarse porque sólo contiene referencias no sensibles.

Las únicas variables de entorno productivas son bootstrap del runtime, por ejemplo `AWS_REGION`, `AWS_APPCONFIG_APPLICATION`, `AWS_APPCONFIG_ENVIRONMENT`, `AWS_APPCONFIG_PROFILE` y referencias no secretas a Secrets Manager. El perfil `local-mock` puede usar `.env` ignorado y valores sintéticos.

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

- La aplicación arranca en `local-mock` sin Meta, Bedrock, Knowledge Bases ni credenciales externas.
- Staging puede activar cada adapter real de forma independiente mediante AppConfig.
- Un provider no configurado falla de forma explícita y controlada; no se hace fallback silencioso a producción con datos sintéticos.
- Health verifica proceso y dependencias esenciales; readiness declara qué integración está deshabilitada o degradada sin imprimir secretos.
- La rotación de Secrets Manager debe ser probada sin recompilar ni cambiar código.
