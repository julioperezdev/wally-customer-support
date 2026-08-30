# Operación y configuración — WCS

Owner: Tech Lead  
Status: `Proposed`  
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

## Configuración productiva

La configuración productiva se divide deliberadamente:

### AWS AppConfig — configuración no sensible

```text
wcs.channel.whatsapp.graph-api-version
wcs.channel.whatsapp.graph-api-base-url
wcs.channel.whatsapp.phone-number-id
wcs.channel.whatsapp.business-account-id
wcs.channel.whatsapp.connect-timeout
wcs.channel.whatsapp.read-timeout
wcs.messaging.retry.max-attempts
wcs.messaging.retry.backoff
wcs.ai.provider
wcs.ai.model-id
wcs.ai.prompt-version
wcs.knowledge.provider
wcs.knowledge.bedrock-knowledge-base-id
wcs.knowledge.pgvector.embedding-model-id
wcs.data.retention-days
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
