# ADR-002 — Conocimiento estático y datos dinámicos en WCS

## Estado

Accepted — 2026-09-03

## Contexto

WCS necesita responder dos clases de preguntas con propiedades diferentes:

1. Preguntas sobre información documental de la tienda: preguntas frecuentes,
   formas de envío, ubicaciones, sedes y explicaciones de políticas.
2. Preguntas sobre información operativa que cambia y debe ser exacta: catálogo,
   talles, colores, precios, stock, carrito y pedidos.

Durante una implementación anterior se utilizó una Lambda detrás de API Gateway
que llamaba a Amazon Bedrock Knowledge Bases mediante `RetrieveAndGenerate`.
Ese patrón es útil para conocimiento documental, pero no debe convertirse en la
fuente de verdad de datos transaccionales de WCS.

## Decisión

WCS utilizará dos caminos de recuperación, ambos detrás de puertos de aplicación:

```text
Mensaje del cliente
        │
        ▼
Bedrock Converse / clasificador de intención
        │
        ├── Conocimiento documental
        │      └── KnowledgeRetriever → Bedrock Knowledge Base
        │                                └── S3 + S3 Vectors
        │
        └── Datos dinámicos
               └── tools WCS → casos de uso → PostgreSQL o adapter transaccional
        │
        ▼
LlmClient / política de respuesta
        │
        ▼
Outbox → adapter de WhatsApp o Telegram
```

### Knowledge Base para conocimiento estático

Se creará una Knowledge Base propia de WCS, separada de cualquier Knowledge
Base de otro producto o dominio. La fuente inicial será un bucket S3 con
documentos Markdown versionados:

```text
faq.md
ubicaciones.md
formas_de_envio.md
cambios_y_devoluciones.md
como_comprar.md
```

La configuración objetivo es:

| Componente | Decisión WCS |
| --- | --- |
| Fuente | S3, con opción futura de sincronizar desde Confluence |
| Vector store | Amazon S3 Vectors |
| Embeddings | Amazon Titan Text Embeddings V2 |
| Dimensión | 1024, `float32` |
| Recuperación | Semántica, limitada por `max-results` |
| Identidad | Bucket, índice, Knowledge Base y service role propios |
| Ingestión | Pipeline explícito, versionado y verificable |
| Metadatos | Tipo de documento, idioma, versión, estado y vigencia cuando aplique |

El patrón de S3 Vectors observado en la Knowledge Base histórica
`bigg-rag-sales-offhours` se reutiliza como referencia técnica, no como recurso
de WCS. Sus documentos pertenecen a otro dominio y no deben mezclarse con el
contenido de Ropa de Programador.

La aplicación usará `KnowledgeRetriever` para obtener contexto limitado y
`LlmClient` para redactar la respuesta. El adapter puede utilizar `Retrieve`
seguido de Bedrock Converse para mantener separadas recuperación y generación;
`RetrieveAndGenerate` queda permitido dentro del adapter cuando simplifique un
flujo documental sin romper los contratos de WCS.

### Datos dinámicos mediante tools y casos de uso

Los datos dinámicos no se vectorizan como mecanismo principal de consulta. La
respuesta debe obtenerse de la fuente transaccional actual:

| Tool WCS | Fuente inicial | Ejemplos de parámetros |
| --- | --- | --- |
| `search_catalog` | PostgreSQL | `name`, `sku`, `size`, `color`, `maxPrice` |
| `get_stock` | PostgreSQL o inventario | `sku`, variante |
| `get_cart` | Servicio transaccional | `customerId` |
| `get_order_status` | Servicio de pedidos | `customerId`, `orderId` |

Bedrock puede proponer una tool y extraer sus argumentos, pero WCS valida los
argumentos y ejecuta el caso de uso. El modelo no tiene credenciales de base de
datos, no ejecuta SQL libre y no puede inventar resultados.

Las consultas de PostgreSQL serán allow-listed y parametrizadas. Los casos de
uso aplicarán límites, ownership, autorización y reglas de privacidad antes de
exponer carrito o pedidos. Para DynamoDB se utilizará un adapter equivalente;
no se agrega una segunda fuente de verdad sólo para habilitar lenguaje natural.

### Fuente de verdad para horarios y políticas

Un documento puede explicar horarios o políticas para mejorar la conversación,
pero no deben existir dos valores autoritativos sin sincronización:

- Los horarios y políticas que gobiernan reglas operativas permanecen en datos
  estructurados y versionados de WCS.
- La Knowledge Base puede contener una representación editorial de esos datos
  para preguntas generales, siempre que se publique desde la misma fuente o se
  verifique su vigencia.

## Seguridad y operación

- Cada Knowledge Base de producto tiene un service role de mínimo privilegio.
- Los tokens y credenciales permanecen en Secrets Manager.
- Los identificadores, modo de proveedor, modelo, región, Knowledge Base ID y
  versión de prompt se resuelven desde AppConfig.
- La ingestión debe informar documentos nuevos, modificados, eliminados y
  fallidos.
- Documentos vencidos o fuera del scope no pueden fundamentar una respuesta.
- No se registran conversaciones completas ni resultados con PII innecesaria.
- El webhook confirma rápidamente; el procesamiento y el envío continúan por
  los componentes durables de WCS.

## Consecuencias

### Positivas

- La información estática puede evolucionar sin cambiar el código de negocio.
- Precio, stock, carrito y pedidos conservan consistencia transaccional.
- La misma orquestación puede funcionar con WhatsApp y Telegram.
- Knowledge Bases y un futuro pgvector pueden implementar el mismo puerto.
- El patrón de la Lambda histórica se aprovecha sin conservar su acoplamiento.

### Costos y límites

- Se debe mantener un pipeline de ingestión y controlar la vigencia documental.
- El catálogo requiere contratos de tools y consultas determinísticas.
- Las preguntas mixtas pueden necesitar más de una recuperación.
- S3 Vectors prioriza búsqueda semántica; si se requiere búsqueda híbrida o
  filtros avanzados se reevaluará OpenSearch o un índice propio.

## Fuera de alcance de esta decisión

- Crear una Knowledge Base sobre la información del gimnasio histórica.
- Usar una Knowledge Base como fuente de stock o estado de pedidos.
- Generar y ejecutar SQL arbitrario desde el LLM.
- Crear Redshift sólo para resolver consultas transaccionales de WCS.
- Elegir todavía entre PostgreSQL, DynamoDB u otro sistema para un futuro
  servicio de pedidos que aún no existe.

## Relación con trabajo y documentación

- Jira: `WCS-11`, `WCS-20`, `WCS-21`.
- Documentación canónica: `WCS — AI Models & Prompt Registry` y
  `WCS — Architecture & Services`.
- Implementación prevista: `adapter/out/knowledge`, `adapter/out/ai` y
  `application`.

## Referencias técnicas

- [Amazon Bedrock Knowledge Bases](https://docs.aws.amazon.com/bedrock/latest/userguide/kb-how-data.html)
- [S3 Vectors con Knowledge Bases](https://docs.aws.amazon.com/AmazonS3/latest/userguide/s3-vectors-bedrock-kb.html)
- [Bedrock tool use](https://docs.aws.amazon.com/bedrock/latest/userguide/tool-use.html)
- [Knowledge Bases para datos estructurados](https://docs.aws.amazon.com/bedrock/latest/userguide/knowledge-base-build-structured.html)
