# Specification baseline — WCS product foundation

Owner: Tech Lead  
Status: `Proposed`  
Last reviewed: 2026-08-30  
Related Jira: `WCS-13`, `WCS-17`, `WCS-18`, `WCS-20`, `WCS-21`, `WCS-22`

## Origen

Documento recibido el 2026-08-27: `whatsapp-cloud-api-chatbot-spec.md`. Este archivo resume el baseline funcional y técnico; la implementación debe usar la versión aprobada en Confluence como referencia canónica.

## Qué define el specification heredado

- Java 25, Spring Boot 4.1.1, Maven, Spring MVC, `RestClient`, PostgreSQL, Flyway, Spring Security, Actuator, JUnit/Mockito/MockMvc/Testcontainers y Docker Compose.
- Webhook de WhatsApp, verificación inicial, firma `X-Hub-Signature-256`, mensajes de texto, historial, LLM desacoplado, envío de respuestas, mocks, idempotencia, reintentos y logs estructurados.
- Endpoints `GET /webhook/whatsapp`, `POST /webhook/whatsapp` y `POST /internal/test/messages`.
- Modelo mínimo de conversación y mensaje.
- Ventana de atención de 24 horas.
- Perfil `mock`, variables de entorno y operación sin credenciales externas.
- Tests unitarios, integración, contrato y ciclo mock completo.

Este baseline de canal se incorpora a la arquitectura definitiva como un adapter de entrada/salida. `WCS-24` conserva la evidencia de conectividad como una PoC histórica; no define la arquitectura ni el despliegue del producto.

## Arquitectura definitiva adoptada

- Monolito modular hexagonal con dominio independiente de Spring, Meta, AWS, JPA y SDKs de IA.
- Meta/WhatsApp detrás de `InboundMessagePort` y `OutboundMessagePort`.
- LLM detrás de `LlmClient`, con `MockLlmClient` y adapter AWS Bedrock.
- RAG detrás de `KnowledgeRetriever`, con adapters para Bedrock Knowledge Bases y PostgreSQL/pgvector.
- PostgreSQL/JPA/Flyway para persistencia; outbox o dispatcher durable para el procesamiento asíncrono.
- AppConfig para configuración no sensible y Secrets Manager para tokens, app secrets, credenciales y keys.
- Los dobles de Meta, Bedrock y RAG deben permanecer disponibles para tests sin
  convertirlos en la configuración normal del runtime.

## Fuera de alcance del baseline de canal

Audio, imágenes, documentos, stickers, ubicación, Flows, llamadas, grupos, pagos, commerce, panel web, multi-tenant, RAG, tools del agente y configuración automática de Meta.

## Fases técnicas declaradas

1. Esqueleto y modo mock.
2. Integración real de Meta.
3. Integración real del LLM.
4. Exposición y endurecimiento.

## Criterios de aceptación heredados

La aplicación debe arrancar en local con Docker/mock, verificar el challenge, persistir una sola vez, completar el ciclo mock, rechazar firma inválida, evitar secretos hardcodeados y permitir que otra persona ejecute el proyecto desde cero.

## Revisión necesaria antes de habilitar producción

El documento es suficiente como baseline técnico, pero no cierra todavía el producto de soporte. Antes de crear las tareas de desarrollo deben aceptarse en Confluence:

- catálogo de consultas y políticas de la tienda;
- fuente de conocimiento autorizada;
- fallback y escalamiento humano;
- tratamiento de pedidos, envíos, cambios, devoluciones y reembolsos;
- privacidad, retención, opt-out y borrado;
- cola durable, ordenamiento y protección contra envíos duplicados;
- proveedor/modelo LLM y evaluación;
- ambientes, despliegue y rollback;
- comportamiento fuera de la ventana de 24 horas.

La implementación de fundaciones técnicas se mantiene separada de la activación real de Meta y puede avanzar con mocks cuando el issue correspondiente esté en `Ready`.
