# Roadmap de producto — WCS

Owner: Product/Tech Lead  
Status: `Proposed`  
Last reviewed: 2026-09-03

## Visión

Permitir que una tienda atienda consultas frecuentes por WhatsApp con respuestas rápidas, trazables y seguras, derivando a una persona cuando el bot no tiene suficiente información o la operación requiere intervención.

## Resultado del primer piloto

Un cliente puede enviar un mensaje de texto y recibir una respuesta del bot por WhatsApp; el sistema conserva el historial mínimo, evita duplicados, registra el resultado y permite operar primero con adaptadores mock y luego con Meta y un LLM real.

## Alcance inicial

Incluye webhook de Meta, verificación, HMAC, mensajes de texto, conversación básica, LLM desacoplado, respuesta por WhatsApp, modo mock, idempotencia, reintentos acotados, logs estructurados, PostgreSQL, tests de contrato/integración y entorno local reproducible.

Fuera de alcance inicial: audio, imágenes, stickers, ubicación, grupos, pagos, WhatsApp Flows, panel web y configuración automática de Meta. El conocimiento documental queda detrás de `KnowledgeRetriever`; la implementación inicial será una Knowledge Base propia de WCS y pgvector permanece como alternativa futura.

## Decisión de conocimiento y datos

Para el desarrollo del MVP se adopta una Knowledge Base propia de WCS para
documentación estática y Bedrock Converse con tools WCS para información
dinámica. La Knowledge Base usará inicialmente S3, S3 Vectors y Titan Text
Embeddings V2 de 1024 dimensiones. PostgreSQL continúa siendo la fuente de
verdad para catálogo, precio, stock, carrito y pedidos; pgvector queda como
alternativa futura detrás de `KnowledgeRetriever`.

La Knowledge Base histórica `bigg-rag-sales-offhours` sólo se utiliza como
referencia de implementación. Sus documentos y recursos no se comparten con
WCS.

## Fases y gates

### Fase 0 — Gobierno y documentación

**Salida:** Confluence, Jira `WCS`, templates, matriz de fuentes de verdad, catálogo de requerimientos, modelo de evidencia y decisiones abiertas.

### Fase 1 — Definición funcional del soporte

**Salida:** actores, políticas de atención, catálogo de preguntas respondibles, fallback, escalamiento humano, privacidad, retención, horarios y matriz de casos de uso.

### Fase 2 — Fundación de producto, mock vertical slice e infraestructura base

**Salida:** Spring Boot modular, PostgreSQL/JPA/Flyway, schema `wcs` sobre el
RDS compartido, puertos y adapters, catálogo demo determinístico, horarios y
políticas versionadas, referencias de imágenes para S3, configuración
local/producción con AppConfig y Secrets Manager, persistencia, deduplicación,
outbox/dispatcher durable, adapters de WhatsApp y Telegram, Mock LLM, Mock
Knowledge Retriever, endpoint interno, tests e infraestructura base con ECR,
AppConfig, Secrets Manager, OIDC y App Runner opcional.

### Fase 3 — Integraciones reales de canales

**Salida:** webhook Telegram protegido, webhook Meta con verificación y firma
`X-Hub-Signature-256`, parsers de payload, clientes `RestClient`, ventana de
atención, reintentos y pruebas controladas con los canales habilitados. Cada
canal continúa detrás de su adapter y comparte el caso de uso de aplicación.

### Fase 4 — LLM, tools y políticas conversacionales

**Salida:** Bedrock Converse detrás de `LlmClient` y del clasificador de
intención, contratos de tools para datos dinámicos, prompts versionados,
límites, guardrails, fallback, evaluación, costo y latencia medidos. El LLM
interpreta la consulta y propone una tool; WCS valida y ejecuta el caso de uso.

### Fase 5 — Conocimiento y RAG

**Salida:** Knowledge Base propia de WCS para documentos estáticos, bucket S3,
S3 Vectors, Titan Text Embeddings V2, pipeline de ingestión versionado,
`KnowledgeRetriever`, evaluación de recuperación y fallback cuando no existe
evidencia suficiente. PostgreSQL continúa siendo la fuente de verdad para
catálogo, precio, stock, carrito y pedidos; pgvector queda como alternativa
futura detrás del mismo puerto.

### Fase 6 — Producción y operación

**Salida:** activación controlada de ambientes, secrets, CI/CD, health/readiness,
CloudWatch, alertas, runbooks, rollback y smoke E2E. La base IaC no implica por
sí misma la activación productiva.

### Fase 7 — Piloto y evolución

**Salida:** métricas de resolución, derivación, latencia, costo y satisfacción; backlog para FAQ/RAG, herramientas, media y multi-tenant sólo si la evidencia lo justifica.

## Estimación preliminar

Supuesto: una persona con experiencia backend/full-stack, sin tiempos de espera de Meta ni diseño visual de un panel.

| Fase | Días-persona |
|---|---:|
| Fase 0 | 2–4 |
| Fase 1 | 3–5 |
| Fase 2 | 6–10 |
| Fase 3 | 3–5 |
| Fase 4 | 3–6 |
| Fase 5 | 4–8 |
| Fase 6 | 5–9 |
| Fase 7 | 2–4 |
| **Total piloto** | **25–46** |

El costo se versiona por fecha:

```text
costo de ingeniería = días-persona × tarifa
costo operativo = AWS + LLM + WhatsApp + observabilidad + soporte
costo total = ingeniería + costo operativo
```

No se fijan precios externos hasta seleccionar proveedor/modelo y confirmar la cuenta de Meta.

## Criterio de avance

Las líneas de trabajo pueden avanzar en paralelo cuando no comparten un contrato pendiente: la falta de configuración real de Meta no bloquea persistencia, mocks, Bedrock mock ni el contrato RAG. No se habilita tráfico productivo si los gates funcionales, de privacidad y operación no tienen evidencia suficiente.
