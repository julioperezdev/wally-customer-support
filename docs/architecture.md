# Arquitectura objetivo — WCS

Owner: Tech Lead  
Status: `Accepted`
Last reviewed: 2026-09-03
Related Jira: `WCS-13`, `WCS-17`, `WCS-18`, `WCS-20`, `WCS-21`, `WCS-22`, `WCS-25`, `WCS-28`, `WCS-29`
Related repository paths: `src/main/java`, `src/main/resources`, `db/migration`  
Decision/source: specification de WhatsApp y re-baseline solicitada el 2026-08-30

## Decisión base

WCS será inicialmente un **monolito modular con arquitectura hexagonal**. Esto permite entregar rápido sin acoplar el dominio a Meta, AWS, un proveedor LLM, una tecnología de retrieval ni una cola específica. La separación modular deja abierta una extracción posterior sólo cuando el volumen o el ownership lo justifique.

La integración de WhatsApp, el LLM y el retrieval se consumen mediante adapters.
La ejecución normal usa las integraciones productivas configuradas en AWS;
los tests conservan dobles para poder verificar persistencia, conversación, IA
y RAG sin depender de servicios externos.

## Mapa lógico

```text
WhatsApp / Telegram / canal futuro
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
  ├── ConversationIntentClassifier / Bedrock Converse
  ├── KnowledgeRetriever para consultas documentales
  ├── WCS tools para datos dinámicos
  ├── LlmClient para respuestas fundamentadas
  ├── ResponsePolicy
  └── OutboundMessagePort
        │
        ├── MetaWhatsAppAdapter
        ├── TelegramAdapter
        ├── MockWhatsAppAdapter
        └── MockTelegramAdapter
        ├── BedrockLlmAdapter
        ├── MockLlmAdapter
        ├── BedrockKnowledgeBaseAdapter
        └── PgVectorKnowledgeAdapter
        ▼
PostgreSQL + Flyway + observabilidad
```

## Frontera entre conocimiento y datos transaccionales

WCS separa el conocimiento documental de los datos que deben ser consultados
con exactitud. Bedrock Knowledge Bases se utilizará para documentos estáticos
de la tienda, mientras que los casos de uso dinámicos se ejecutarán sobre las
fuentes transaccionales de WCS.

```text
Mensaje del cliente
        │
        ▼
Bedrock Converse / ConversationIntentClassifier
        │
        ├── Consulta documental
        │      └── KnowledgeRetriever → WCS Knowledge Base
        │                                └── S3 + S3 Vectors
        │
        └── Consulta dinámica
               └── tool WCS → caso de uso → PostgreSQL/DynamoDB/API
        │
        ▼
LlmClient + ResponsePolicy
        │
        ▼
Outbox → WhatsApp / Telegram
```

### Conocimiento documental

La Knowledge Base de WCS tendrá bucket S3, índice S3 Vectors, Titan Text
Embeddings V2 de 1024 dimensiones y service role propios. Los documentos
iniciales serán FAQ, ubicación, sedes, formas de envío, cómo comprar y
cambios/devoluciones. El patrón histórico de `bigg-rag-sales-offhours` sirve
como referencia de configuración, pero sus recursos y documentos no se
comparten con WCS.

El contenido se ingesta mediante un pipeline explícito y versionado. Los
documentos pueden llevar metadatos de tipo, idioma, versión, estado y vigencia.
Una respuesta no puede basarse en documentos vencidos, fuera del scope o sin
evidencia suficiente.

### Datos dinámicos

El LLM puede seleccionar una tool y extraer argumentos, pero WCS valida esos
argumentos y ejecuta el caso de uso. Las tools iniciales son:

| Tool | Fuente | Parámetros principales |
| --- | --- | --- |
| `search_catalog` | PostgreSQL | nombre, SKU, talle, color, precio máximo |
| `get_stock` | PostgreSQL/inventario | SKU y variante |
| `get_cart` | Servicio transaccional | identidad del cliente |
| `get_order_status` | Servicio de pedidos | identidad y número de pedido |

El LLM no tiene acceso directo a credenciales, no genera SQL ejecutable y no
puede inventar precios, stock, carrito ni estados de pedidos. PostgreSQL usa
consultas parametrizadas y allow-listed; DynamoDB u otra fuente futura se
integra mediante un adapter equivalente.

Los horarios y políticas que gobiernan reglas operativas permanecen en datos
estructurados y versionados. La Knowledge Base puede contener una copia
editorial para responder preguntas generales sólo si se mantiene sincronizada
con la fuente de verdad.

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
| `adapter/in/web` | HTTP, validación, challenge, firma y secret de webhook | `application`, Spring Web |
| `adapter/out/whatsapp` y `adapter/out/telegram` | APIs de canal, mocks y mapeo de errores | `application`, RestClient |
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
    Channel channel();
    void send(OutboundMessage message);
}

interface LlmClient {
    String generateReply(ConversationContext context);
}

interface ConversationIntentClassifier {
    ConversationIntentDecision classify(String message);
}

interface KnowledgeRetriever {
    List<KnowledgeChunk> retrieve(KnowledgeQuery query);
}

interface CatalogRepository {
    List<CatalogProduct> search(CatalogQuery query);
}

interface SupportConfigurationRepository {
    List<BusinessHour> findBusinessHours();
    Optional<SupportPolicy> findActivePolicy(String policyKey);
}

interface ConversationRepository { /* load/save aggregate */ }
interface MessageRepository { /* idempotency and state */ }
interface OutboxRepository { /* durable outbox and retry state */ }
```

Los nombres y contratos son internos de WCS; ningún adapter debe filtrarlos con tipos de Meta, Telegram o AWS. El `OutboxDispatcher` selecciona el adapter mediante `OutboundMessage.channel`; el dominio no conoce URLs, tokens, templates ni payloads de proveedores.

El flujo conversacional pasa por `ConversationOrchestrator`, que usa el puerto
`ConversationIntentClassifier` para convertir lenguaje natural en una decisión
estructurada. Las decisiones permitidas son `GREETING`, `CATALOG_SEARCH`,
`BUSINESS_HOURS`, `POLICY_QUERY`, `HUMAN_HANDOFF`, `GENERAL_SUPPORT` y
`UNKNOWN`. El orquestador invoca el caso de uso correspondiente; no ejecuta
acciones derivadas directamente de texto libre.

En `CATALOG_SEARCH`, el clasificador sólo extrae filtros
`name`/`sku`/`size`/`color`. El catálogo se consulta con esos filtros y
resultados determinísticos de PostgreSQL. El LLM no genera SQL ni inventa
precio, stock, políticas u horarios.
Las imágenes se modelan como referencias de objeto S3 y su envío por WhatsApp
queda fuera del MVP.

## Configuración por ambiente

La aplicación tendrá una única configuración lógica y distintos proveedores de configuración:

| Tipo | Fuente productiva | Ejemplos |
| --- | --- | --- |
| No sensible | AWS AppConfig | Graph API y Telegram base URLs, Phone Number ID, WABA ID, activación/adapter de canal, timeouts, retries, provider mode, Bedrock model ID, Knowledge Base ID, prompt version, retention |
| Secreto | AWS Secrets Manager | Meta access token/app secret/verify token, Telegram bot token y webhook secret, credenciales de base de datos y keys de proveedores externos |
| Local | `application.properties` + cadena estándar de credenciales AWS | Mismo runtime `prod` para validar integraciones; nunca se versionan credenciales |

No se requieren variables de entorno de aplicación para el bootstrap normal.
Los identificadores estables de AppConfig están versionados; la región y las
credenciales se resuelven mediante el proveedor estándar del SDK de AWS. No se
usará un `.env` como mecanismo de configuración de producción ni se imprimirán
valores resueltos.

`AwsExternalConfigurationEnvironmentPostProcessor` carga AppConfig antes del
binding de `@ConfigurationProperties` y resuelve desde Secrets Manager sólo los
campos allow-listed de los roles `database`, `whatsapp`, `telegram` y `runtime`. El runtime
normal usa AWS y el fail-fast evita operar con configuración parcial; los tests
deshabilitan las fuentes externas y usan datos sintéticos.

## Flujos críticos

1. El adapter de canal recibe el evento.
2. Se valida la firma sobre el body original antes de parsear.
3. Se transforma a un comando interno y se persiste de forma idempotente.
4. La primera fundación genera la respuesta mediante puertos y deja el envío en un outbox durable; la separación del processor asíncrono completo queda en la siguiente iteración.
5. `ConversationOrchestrator` clasifica la intención y ejecuta catálogo, horarios, políticas, handoff o soporte general.
6. Las consultas documentales consultan `KnowledgeRetriever`; las consultas
   dinámicas usan tools WCS y fuentes transaccionales antes de generar la
   respuesta detrás de `LlmClient`.
7. `ResponsePolicy` validará grounding, privacidad, ventana de atención y fallback antes de habilitar producción.
8. El dispatcher envía la respuesta y registra el resultado/reintento.
9. Logs, métricas y trazas usan correlación y metadatos sanitizados, nunca payloads completos.

## Decisiones abiertas antes de producción

- SQS/SQS FIFO, outbox polling u otro dispatcher durable.
- Modelo Bedrock y guardrails aplicables.
- Evaluar pgvector como alternativa si se necesitan chunks, filtros, búsqueda
  híbrida o control de indexación que no cubra la Knowledge Base de WCS.
- Handoff humano y ownership de conversación.
- Topología de hosting y terminación HTTPS/mTLS.
- Retención, borrado, acceso y estrategia de cifrado.
- Persistencia del handoff humano y ownership de la tarea priorizada.
