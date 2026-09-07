# WCS — baseline restaurable antes de la plataforma de agentes

Estado: `inmutable`

Fecha de captura: `2026-09-07`

Issue: [`WCS-44`](https://julioperezdev.atlassian.net/browse/WCS-44)

## Identidad del baseline

Este documento describe el punto de restauración de Wally Customer Support
antes de iniciar el rediseño del runtime conversacional y la plataforma de
agentes.

| Referencia | Valor |
| --- | --- |
| Commit de `main` | `c6861becb2b4db215d5be7148f7697f8cbd8f6f8` |
| Merge | PR #60, `fix/WCS-43-catalog-follow-ups` |
| Tag | `wcs-baseline-2026-09-07` |
| Branch de respaldo | `backup/wcs-baseline-2026-09-07` |
| Repositorio | `julioperezdev/wally-customer-support` |
| Región AWS | `us-east-1` |
| Cuenta AWS | `438225605070` |

El tag y la branch fueron publicados en GitHub. La branch es una referencia de
respaldo; el tag es la referencia recomendada para reconstruir exactamente el
código de este momento.

## Qué contiene el respaldo

El commit y sus referencias Git conservan:

- código Java/Spring Boot del monolito modular;
- tests unitarios y de integración con Testcontainers PostgreSQL;
- migraciones Flyway V1 a V8;
- configuración bootstrap de Spring, AppConfig y Secrets Manager;
- módulos Terraform y ejemplos de variables no sensibles;
- workflows de GitHub Actions y scripts de despliegue;
- documentos Markdown de la Knowledge Base de WCS;
- dashboards, queries y configuración de Grafana;
- documentación funcional, técnica, operativa y de observabilidad.

El snapshot declarativo del schema está en
[`schema.sql`](schema.sql). Se construyó a partir de las migraciones V1, V2 y
V4 a V8. La migración V3 contiene datos demo y por eso no se reproduce dentro
del snapshot estructural.

## Estado funcional respaldado

La versión contiene estos flujos:

- webhooks y adapters para WhatsApp Cloud API y Telegram;
- deduplicación de mensajes por canal e identificador externo;
- persistencia de conversaciones, mensajes, intentos y outbox;
- catálogo demo en PostgreSQL con productos, variantes, SKU, precio, talle,
  color y stock;
- horarios y políticas demo en PostgreSQL;
- Knowledge Base documental de WCS para consultas estáticas;
- clasificación de intención y soporte general mediante adapters de Bedrock;
- respuestas de catálogo basadas en datos de PostgreSQL;
- memoria conversacional opcional, resumen y preferencias explícitas;
- logs estructurados, métricas de IA y dashboard de Grafana;
- CI/CD con validación Maven, imagen inmutable en ECR y App Runner.

## Estado de infraestructura respaldado

La configuración declarativa de este baseline contempla:

- ECR: `wally-customer-support-prod-backend`;
- App Runner: `wally-customer-support-prod-backend`;
- endpoint conocido de health-check: `https://guapajjmta.us-east-1.awsapprunner.com/actuator/health`;
- AppConfig application: `wally-customer-support`;
- AppConfig environment: `prod`;
- AppConfig profile: `runtime`;
- Secrets Manager para referencias de database, WhatsApp y Telegram;
- RDS PostgreSQL existente de `tesis-dev`, aislado mediante el schema `wcs`;
- Knowledge Base propia de WCS con documentos en `knowledge-base/wcs/`;
- S3 Vectors y Titan Text Embeddings V2 configurados por Terraform;
- IAM/OIDC para GitHub Actions y roles separados de Terraform y App Runner.

La infraestructura de red y la instancia RDS compartida no son propiedad de
este repositorio. Terraform WCS sólo referencia el RDS existente y administra
el schema mediante Flyway.

## Schema y migraciones

El schema lógico actual es `wcs`. Las migraciones aplicadas y su responsabilidad
son:

| Migración | Responsabilidad |
| --- | --- |
| V1 | conversaciones, mensajes, intentos de procesamiento y outbox |
| V2 | productos, variantes, horarios y políticas |
| V3 | datos demo sintéticos de catálogo y soporte; no forma parte del snapshot estructural |
| V4 | generalización de identificadores para soportar múltiples canales |
| V5 | tipo de producto e índice de catálogo |
| V6 | estado de memoria conversacional por conversación |
| V7 | resumen versionado de conversación |
| V8 | preferencias explícitas con TTL y ownership |

Las migraciones Flyway son la fuente de verdad para reconstruir el schema. No
se deben editar migraciones ya aplicadas ni ejecutar un `DROP SCHEMA` sobre el
RDS compartido para volver a este punto.

## Configuración y secretos

El bootstrap estable de la aplicación utiliza:

```text
AppConfig application: wally-customer-support
AppConfig environment: prod
AppConfig profile: runtime
Database schema: wcs
Spring profile: prod
```

Las referencias conocidas de Secrets Manager son:

```text
wcs/prod/database
wcs/prod/whatsapp
wcs/prod/telegram
```

La configuración real de AppConfig puede haber sido editada fuera del
repositorio y algunos recursos Terraform tienen `ignore_changes`. Por eso el
contenido vivo de AppConfig debe verificarse en AWS antes de un rollback.

No se incluyen en Git, en este documento ni en el snapshot:

- access tokens, passwords, app secrets o webhook secrets;
- valores de Secrets Manager;
- valores sensibles de GitHub Environment;
- conversaciones reales, PII ni datos de clientes;
- Terraform state remoto;
- logs históricos de CloudWatch/Grafana;
- configuración externa de Meta o Telegram.

Es una exclusión deliberada de seguridad. Esos recursos deben recuperarse en
su cuenta AWS o en el backup seguro correspondiente, no desde este repositorio.

## Procedimiento de restauración del código

Verificar primero la identidad del tag:

```bash
git fetch origin --tags
git rev-parse wcs-baseline-2026-09-07
git show -s --format='%H %aI %s' wcs-baseline-2026-09-07
```

Crear una branch de restauración sin modificar `main`:

```bash
git switch -c restore/wcs-baseline-2026-09-07 wcs-baseline-2026-09-07
```

Para restaurar el entorno compartido:

1. Abrir un PR desde la branch de restauración hacia `main`.
2. Verificar Maven, Testcontainers, seguridad y el plan de Terraform.
3. Revisar que el plan no contenga destrucciones ni reemplazos.
4. Hacer merge sólo después de aprobar el rollback.
5. Ejecutar el deploy manual de backend desde `main`.
6. Validar `/actuator/health` y los smoke tests de canales.

No se recomienda desplegar directamente desde el tag o forzar `main`, porque
se perdería la trazabilidad normal de Jira y GitHub.

## Procedimiento de restauración de base de datos

La restauración segura debe hacerse sobre una base de test o una instancia
aislada. El baseline estructural se reconstruye aplicando Flyway V1 a V8 y,
si se necesitan datos de demostración, V3.

```bash
mvn -B verify
```

El comando anterior valida el schema mediante PostgreSQL de Testcontainers. En
RDS compartido sólo se debe ejecutar Flyway con el usuario autorizado y nunca
se debe borrar el schema productivo para forzar una reconstrucción.

El estado real del RDS se debe contrastar con `flyway_schema_history`. Si
existen cambios manuales fuera de las migraciones, deben documentarse como
divergencia antes de cualquier rollback.

## Procedimiento de restauración de infraestructura

El backend de Terraform de producción está separado por el key definido en
`infra/environments/prod/backend.tf`:

```text
wally-customer-support/environments/prod/terraform.tfstate
```

Para revisar el baseline sin modificar AWS:

```bash
terraform -chdir=infra/environments/prod init -backend=false -input=false
terraform -chdir=infra/environments/prod fmt -check -recursive
terraform -chdir=infra/environments/prod validate
```

Un rollback real requiere recuperar las variables revisadas del Environment
`production`, inicializar el backend remoto correcto y ejecutar primero un
`plan`. No se debe reutilizar el state de `tesis-dev`, copiar su `tfvars` ni
aprobar un plan con recursos inesperados.

## Criterio para volver al baseline

Se debe volver a este punto si el nuevo runtime de agentes presenta una
regresión que no pueda aislarse con feature flags o rollback de configuración,
por ejemplo:

- respuestas con datos no sustentados;
- acceso indebido a una herramienta o fuente de datos;
- pérdida de idempotencia, outbox o ownership;
- aumento no controlado de coste o latencia;
- degradación de los canales existentes;
- migración de schema incompatible.

El rollback de prompts, modelos o flags debe intentarse primero. El rollback de
código e infraestructura debe seguir el procedimiento anterior y quedar
registrado en WCS-44.
