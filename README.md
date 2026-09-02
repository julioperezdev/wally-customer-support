# WCS — Wally Customer Support

Sistema de customer support conversacional para una tienda, inicialmente sobre WhatsApp Cloud API.

El repositorio contiene la documentación y la base técnica del producto. La implementación definitiva se organiza como un monolito modular con adapters para WhatsApp, LLM, RAG y persistencia; la activación productiva queda gobernada por los gates funcionales y operativos de WCS.

## Fuentes de verdad

- Producto, negocio, arquitectura narrativa y operación: Confluence, espacio del proyecto WCS.
- Trabajo ejecutable, estados, responsables, dependencias y evidencia: Jira, proyecto `WCS`.
- Código, migraciones, contratos ejecutables, tests y workflows: este repositorio.
- Specification inicial recibido: [`docs/specification-baseline.md`](docs/specification-baseline.md).

## Documentación local

- [`docs/documentation-system.md`](docs/documentation-system.md): modelo Confluence/Jira/repo y reglas de trazabilidad.
- [`docs/roadmap.md`](docs/roadmap.md): visión, fases, gates y estimación inicial.
- [`docs/functional-requirements.md`](docs/functional-requirements.md): requerimientos y casos de uso iniciales.
- [`docs/architecture.md`](docs/architecture.md): mapa de servicios y flujos técnicos.
- [`docs/data-model.md`](docs/data-model.md): modelo conceptual y evolución del schema.
- [`docs/ai.md`](docs/ai.md): registro de modelos, prompts, guardrails y evaluación.
- [`docs/testing-strategy.md`](docs/testing-strategy.md): estrategia y evidencia de testing.
- [`docs/operations.md`](docs/operations.md): CI/CD, observabilidad, seguridad y operación.
- [`infra/README.md`](infra/README.md): base Terraform de AWS, state separado y servicios preparados.
- [`docs/queries.md`](docs/queries.md): consultas SQL y CloudWatch sanitizadas.
- [`docs/agents/playbook.md`](docs/agents/playbook.md): reglas para agentes de IA.
- [`planning/jira-backlog.md`](planning/jira-backlog.md): backlog inicial listo para crear en Jira.

## Fundación ejecutable

La issue `WCS-24` conserva la evidencia histórica de la prueba inicial de WhatsApp. La implementación actual vive en un monolito modular: el dominio y los casos de uso no conocen Meta, AWS, JPA ni el proveedor de IA.

Requisitos: Java 25, Maven y Docker para PostgreSQL local.

```bash
cp .env.example .env
set -a
source .env
set +a
docker compose up -d postgres
mvn clean test
SPRING_PROFILES_ACTIVE=local-mock mvn spring-boot:run
```

Para levantar localmente contra el ambiente `prod` de AWS, la aplicación toma
el nombre estable de AppConfig desde `application.properties`, usa
`SPRING_PROFILES_ACTIVE` para seleccionar el environment y resuelve las
referencias a Secrets Manager:

```bash
AWS_PROFILE=julio_dev \
AWS_REGION=us-east-1 \
SPRING_PROFILES_ACTIVE=prod \
AWS_APPCONFIG_ENABLED=true \
AWS_SECRETS_MANAGER_ENABLED=true \
mvn spring-boot:run
```

El documento de AppConfig debe contener las referencias
`wcs.external-config.secrets-manager.database-secret-id` y
`wcs.external-config.secrets-manager.whatsapp-secret-id`. No se pasan IDs de
AppConfig ni contraseñas por variables de entorno.

El endpoint de verificación es `GET /webhook/whatsapp` y el webhook es `POST /webhook/whatsapp`. En `local-mock`, el webhook persiste el mensaje, consulta el retriever mock, genera una respuesta mock y la despacha por el adapter mock. La deduplicación se hace en PostgreSQL mediante `external_message_id`; el outbox sobrevive a reinicios.

Para probar la integración real con Meta, usar un `.env` local separado, cambiar `WCS_WHATSAPP_ADAPTER=meta` y completar credenciales rotadas. Los secretos productivos deben resolverse desde Secrets Manager; AppConfig contiene sólo configuración no sensible.

```env
WCS_WHATSAPP_ADAPTER=meta
WHATSAPP_GRAPH_API_VERSION=v25.0
WHATSAPP_GRAPH_API_BASE_URL=https://graph.facebook.com
WHATSAPP_PHONE_NUMBER_ID=<runtime-value>
WHATSAPP_ACCESS_TOKEN=<secret-runtime-value>
WHATSAPP_VERIFY_TOKEN=<secret-runtime-value>
META_APP_SECRET=<secret-runtime-value>
```

El adapter Meta soporta texto y templates aprobados con parámetros de body. La evidencia de la PoC debe ser sanitizada y quedar en [WCS-24](https://julioperezdev.atlassian.net/browse/WCS-24); la fundación y sus pruebas se registran en [WCS-13](https://julioperezdev.atlassian.net/browse/WCS-13).

## Estado actual

**Fundación técnica — en implementación:** arquitectura, operación y modelo de datos describen la fundación modular, AppConfig/Secrets Manager, JPA/PostgreSQL, Bedrock y RAG desacoplados. Las páginas canónicas siguen en `Proposed` hasta la aceptación funcional correspondiente.

La branch de trabajo es `feature/WCS-13-product-foundation` y está asociada al repositorio GitHub `julioperezdev/wally-customer-support`. No contiene credenciales productivas.

La infraestructura base sigue el patrón de `tesis-dev`, pero WCS no crea otro
RDS: consume el existente y usa el schema `wcs`. El stack Terraform deja
App Runner desactivado hasta completar la configuración AWS y la conectividad
privada. La validación local se ejecuta sin backend remoto:

```bash
terraform -chdir=infra/environments/prod init -backend=false
terraform -chdir=infra/environments/prod fmt -check -recursive
terraform -chdir=infra/environments/prod validate
```
