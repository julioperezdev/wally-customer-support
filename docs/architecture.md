# Arquitectura objetivo — WCS

Owner: Tech Lead  
Status: `Proposed`  
Last reviewed: 2026-08-30  
Related Jira: `WCS-13`, `WCS-17`, `WCS-18`, `WCS-20`, `WCS-21`, `WCS-22`  
Related repository paths: `src/main/java`, `src/main/resources`, `db/migration`  
Decision/source: specification de WhatsApp y re-baseline solicitada el 2026-08-30

## Decisión base

WCS será inicialmente un **monolito modular con arquitectura hexagonal**. Esto permite entregar rápido sin acoplar el dominio a Meta, AWS, un proveedor LLM, una tecnología de retrieval ni una cola específica. La separación modular deja abierta una extracción posterior sólo cuando el volumen o el ownership lo justifique.

La integración de WhatsApp puede estar caída, sin credenciales o sin configuración de Meta y el sistema debe continuar arrancando en `local-mock`, ejecutando tests y permitiendo desarrollar persistencia, conversación, IA y RAG.

## Mapa lógico

```text
WhatsApp / canal futuro
        │ HTTPS, firma, parseo
        ▼
Inbound Adapter
        │ InboundMessageCommand
        ▼
Conversation Application
  ├── ownership y deduplicación
  ├── persistencia transaccional
  ├── outbox / dispatch durable
  └── políticas de atención
        ▼
Message Processor
  ├── ConversationContextBuilder
  ├── KnowledgeRetriever
  ├── LlmClient
  ├── ResponsePolicy
  └── OutboundMessagePort
        │
        ├── MetaWhatsAppAdapter
        ├── MockWhatsAppAdapter
        ├── BedrockLlmAdapter
        ├── MockLlmAdapter
        ├── BedrockKnowledgeBaseAdapter
        └── PgVectorKnowledgeAdapter
        ▼
PostgreSQL + Flyway + observabilidad
```

## Topología AWS base

La primera base de infraestructura sigue la separación de `tesis-dev` sin
duplicar su base de datos:

```text
GitHub Actions
    │ OIDC, sin access keys
    ▼
ECR ──► App Runner (creación desactivada hasta completar los gates)
                         │
                         ├── AppConfig: configuración no sensible
                         ├── Secrets Manager: secrets de WCS
                         │                    + referencia al secret del RDS
                         ├── VPC connector existente
                         │        ▼
                         │   RDS PostgreSQL de tesis-dev
                         │        └── schema wcs, administrado por Flyway
                         └── Bedrock (permiso IAM opcional)
```

Terraform administra ECR, AppConfig, el contenedor de secrets de WCS, roles
IAM/OIDC y la configuración opcional de App Runner. No administra la instancia
RDS, la VPC ni sus security groups. El consumo del RDS se hace con data sources
y una key de state separada; el schema `wcs` es la frontera de ownership de la
aplicación.

## Módulos y responsabilidades

| Módulo | Responsabilidad | Dependencias permitidas |
| --- | --- | --- |
| `domain` | Entidades, value objects, estados y reglas puras | Java |
| `application` | Casos de uso, puertos, políticas y orquestación | `domain` |
| `adapter/in/web` | HTTP, validación, challenge y firma | `application`, Spring Web |
| `adapter/out/whatsapp` | Meta Cloud API, mocks y mapeo de errores | `application`, RestClient |
| `adapter/out/ai` | Bedrock, mocks, prompts y métricas | `application`, AWS SDK |
| `adapter/out/knowledge` | Knowledge Bases, pgvector y mocks | `application`, AWS SDK/JDBC |
| `adapter/out/persistence` | JPA, repositorios y Flyway | `application`, Spring Data |
| `infrastructure` | Configuración, seguridad, scheduling y observabilidad | Spring/AWS |

El dominio no importa Spring, Meta, Graph API, Bedrock, SDKs de LLM ni JPA. Los controllers son delgados y no contienen prompts ni reglas de negocio.

## Puertos principales

```java
interface InboundMessagePort {
    InboundMessageResult accept(InboundMessageCommand command);
}

interface OutboundMessagePort {
    void send(OutboundMessage message);
}

interface LlmClient {
    String generateReply(ConversationContext context);
}

interface KnowledgeRetriever {
    List<KnowledgeChunk> retrieve(KnowledgeQuery query);
}

interface ConversationRepository { /* load/save aggregate */ }
interface MessageRepository { /* idempotency and state */ }
interface OutboxRepository { /* durable outbox and retry state */ }
```

Los nombres y contratos son internos de WCS; ningún adapter debe filtrarlos con tipos de Meta o AWS.

## Configuración por ambiente

La aplicación tendrá una única configuración lógica y distintos proveedores de configuración:

| Tipo | Fuente productiva | Ejemplos |
| --- | --- | --- |
| No sensible | AWS AppConfig | Graph API version/base URL, Phone Number ID, WABA ID, timeouts, retries, provider mode, Bedrock model ID, Knowledge Base ID, prompt version, retention |
| Secreto | AWS Secrets Manager | Meta access token, Meta app secret, webhook verify token, credenciales de base de datos y keys de proveedores externos |
| Local | `.env` ignorado + Docker Compose | Valores sintéticos o credenciales de desarrollo, nunca valores productivos |

Las variables de entorno quedan limitadas al bootstrap del runtime (`AWS_REGION`, identificadores de AppConfig/Secrets Manager y perfil local). No se usará un `.env` como mecanismo de configuración de producción ni se imprimirán valores resueltos.

## Flujos críticos

1. El adapter de canal recibe el evento.
2. Se valida la firma sobre el body original antes de parsear.
3. Se transforma a un comando interno y se persiste de forma idempotente.
4. La primera fundación genera la respuesta mediante puertos y deja el envío en un outbox durable; la separación del processor asíncrono completo queda en la siguiente iteración.
5. El contexto consulta conocimiento y genera una respuesta detrás de `KnowledgeRetriever` y `LlmClient`.
6. `ResponsePolicy` validará grounding, privacidad, ventana de atención y fallback antes de habilitar producción.
7. El dispatcher envía la respuesta y registra el resultado/reintento.
8. Logs, métricas y trazas usan correlación y metadatos sanitizados, nunca payloads completos.

## Decisiones abiertas antes de producción

- SQS/SQS FIFO, outbox polling u otro dispatcher durable.
- Modelo Bedrock y guardrails aplicables.
- Knowledge Bases o índice propio en pgvector; pueden coexistir detrás de `KnowledgeRetriever`.
- Handoff humano y ownership de conversación.
- Topología de hosting y terminación HTTPS/mTLS.
- Retención, borrado, acceso y estrategia de cifrado.
