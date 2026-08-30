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
- [`docs/queries.md`](docs/queries.md): consultas SQL y CloudWatch sanitizadas.
- [`docs/agents/playbook.md`](docs/agents/playbook.md): reglas para agentes de IA.
- [`planning/jira-backlog.md`](planning/jira-backlog.md): backlog inicial listo para crear en Jira.

## Evidencia histórica de WhatsApp

La issue `WCS-24` conserva la evidencia histórica de una prueba de challenge, firma HMAC, recepción de texto y envío de una respuesta fija mediante WhatsApp Cloud API. No se debe convertir esa implementación aislada en el producto: el código definitivo debe reutilizar los contratos y mover Meta detrás de un adapter.

Requisitos: Java 25 y Maven.

```bash
cp .env.example .env
# completar .env sólo con una credencial nueva/rotada y un recipient de prueba allowlisted
set -a
source .env
set +a
mvn clean test
mvn spring-boot:run
```

El endpoint de verificación es `GET /webhook/whatsapp` y el webhook es `POST /webhook/whatsapp`. Para Meta se necesita una URL HTTPS temporal y se deben registrar Callback URL y Verify Token sin copiar secretos al repositorio, Jira, Confluence o logs.

La respuesta del webhook puede ser texto o template. Para `hello_world` sin parámetros:

```env
POC_REPLY_MODE=template
POC_TEMPLATE_NAME=hello_world
POC_TEMPLATE_LANGUAGE_CODE=en_US
POC_TEMPLATE_BODY_PARAMETERS=
```

Para un template con parámetros de body, se usa `|` para conservar el orden requerido por Meta:

```env
POC_REPLY_MODE=template
POC_TEMPLATE_NAME=order_confirmation
POC_TEMPLATE_LANGUAGE_CODE=en_US
POC_TEMPLATE_BODY_PARAMETERS=John Doe|123456|Aug 30, 2026
```

El template debe existir y estar aprobado en la cuenta de WhatsApp. La PoC soporta componentes `body` con parámetros de texto; headers, media, botones y otros tipos de parámetros quedan fuera de este alcance.

La evidencia de la PoC debe ser sanitizada y quedar en [WCS-24](https://julioperezdev.atlassian.net/browse/WCS-24). El resultado alimenta `WCS-17`, `WCS-18` y `WCS-19`, pero no reemplaza sus gates.

## Estado actual

**Re-baseline técnica — propuesta:** arquitectura, operación y modelo de datos ya describen la fundación modular, AppConfig/Secrets Manager, JPA/PostgreSQL, Bedrock y RAG desacoplados. Las páginas canónicas están en `Proposed` hasta la aceptación correspondiente.

La carpeta local todavía no es un checkout Git; antes de una entrega de código debe asociarse a su repositorio y branch de Jira. No contiene credenciales productivas.
