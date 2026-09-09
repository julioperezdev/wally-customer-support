# Política de privacidad y retención de memoria conversacional — WCS

Owner: Product/Tech Lead  
Status: `Proposed — pending legal and business approval`  
Last reviewed: 2026-09-06  
Related Jira: `WCS-26`, `WCS-34`, `WCS-35`, `WCS-36`, `WCS-37`
Related decision: [`003-conversational-memory-boundary.md`](decisions/003-conversational-memory-boundary.md)

## Propósito y alcance

Esta política define la memoria conversacional de corto plazo que WCS puede
usar para continuar una conversación. No constituye por sí sola la política
legal definitiva de la tienda ni reemplaza los requisitos aplicables de
privacidad, consumo o protección de datos.

El alcance de esta política es el contrato de memoria y sus límites. `WCS-35`
implementa la primera persistencia en PostgreSQL, pero no la activa por defecto:
`wcs.conversation.memory.enabled=false` mantiene el adapter no-op hasta que la
política sea aprobada.

## Principios

1. WCS es responsable del estado que usa para responder.
2. `conversationId` interno y `actorId` pseudónimo son la frontera de
   ownership.
3. Un canal nunca puede consultar la memoria de otro actor o conversación.
4. La memoria no es fuente de verdad para stock, precio, carrito, pedidos ni
   otros datos transaccionales.
5. Se guarda el mínimo contexto necesario, durante el mínimo tiempo necesario.
6. Los secretos, tokens, payloads completos de proveedores y PII innecesaria no
   forman parte de la memoria.

## Clasificación de datos

| Dato | Tratamiento | Retención inicial recomendada |
| --- | --- | --- |
| `conversationId` interno | Identificador de ownership | Mientras exista la conversación operativa |
| `actorId` pseudónimo | Identificador técnico, no teléfono | Mientras exista la conversación operativa |
| Mensajes recientes | Contexto acotado para interpretar el siguiente turno | 24 horas desde la última actualización, pendiente de aprobación |
| Resumen conversacional | Contexto comprimido del prefijo antiguo; no es autoridad transaccional | Igual que la memoria de sesión, pendiente de aprobación |
| Filtros de búsqueda actuales | Estado temporal; se recalcula o limpia por turno | Igual que la memoria de sesión |
| Preferencias explícitas | Preferencias de bajo riesgo expresadas o confirmadas por el cliente; contexto auxiliar | 24 horas desde `updated_at`, pendiente de aprobación |
| Stock, precio, carrito y pedidos | Se consulta en PostgreSQL/servicio transaccional | No se convierte en memoria |
| Respuestas y documentos RAG | Evidencia de la consulta actual | No se guarda como preferencia por esta fase |
| Secretos y tokens | Nunca se almacenan en memoria | Nunca |

## Límites por defecto

La política de WCS recomienda inicialmente:

- TTL de memoria de sesión: `24h` desde `updatedAt`.
- TTL de preferencias explícitas: `24h` desde `updatedAt`, sujeto al mismo gate
  de aprobación de retención.
- Máximo de `20` mensajes recientes.
- Máximo de `2.000` caracteres por mensaje usado como contexto.
- Máximo de `5` preferencias por contexto y `64` caracteres por valor.
- Al superar el máximo de mensajes, conservar sólo los más recientes.
- Eliminar mensajes vacíos y recortar espacios antes de guardar.
- El resumen se activa sólo por umbral de cantidad o caracteres, conserva una
  ventana reciente y se versiona junto al checkpoint de memoria.
- Si el resumen falla o es inválido, se usa la ventana reciente acotada y no se
  bloquea la atención.
- No incluir en el resumen teléfonos, emails, tokens, secretos, pagos ni PII
  innecesaria.

Estos valores están codificados en `ConversationMemoryPolicy.recommended()` y
deben convertirse en configuración administrada antes de la persistencia
productiva. La ventana no debe ampliarse para compensar una mala clasificación;
eso se evalúa con métricas de contexto, latencia y costo.

## Borrado, opt-out y expiración

- `clear(conversationId, actorId)` elimina el estado de memoria asociado al
  actor y conversación.
- Un opt-out debe ejecutar el borrado de memoria antes de continuar el flujo.
- El borrado de una conversación elimina también las preferencias con alcance
  `CONVERSATION`; el borrado de un actor elimina sus preferencias de cualquier
  alcance.
- Una carga posterior a la expiración devuelve estado vacío y elimina la
  entrada vencida.
- El borrado de filas históricas de `messages` es una operación diferente y
  requiere una política de retención y un caso de uso de eliminación propio.
- No se deben reconstituir datos borrados desde logs, outbox, backups o un
  proveedor de memoria externo sin una base legal y operativa aprobada.

`WCS-26` agrega una retención operativa separada del TTL de memoria: el cuerpo
de los mensajes se redacciona a los 30 días y sus metadatos se eliminan a los
90 días, en lotes idempotentes y con un evento agregado de auditoría. El job
no borra métricas agregadas ni logs operativos. La configuración permanece en
`false` por defecto hasta que Product/Legal apruebe los períodos definitivos.

La solicitud `BAJA`, `STOP` o equivalente se atiende antes de cualquier llamada
a IA. WCS guarda una supresión pseudonimizada `DO_NOT_CONTACT`, limpia la
memoria y preferencias de la conversación y no genera una respuesta/outbox
automático. Los futuros flujos proactivos deben consultar la misma supresión
antes de enviar; no existe todavía un flujo para revocar la supresión.

La extracción automática de preferencias queda fuera de esta fase. Sólo se
persisten preferencias explícitas o confirmadas y, inicialmente, el color
preferido. No se guardan como preferencias hechos transaccionales, precios,
stock, carritos, pedidos ni credenciales.

`WCS-38` permite capturar frases explícitas mediante un parser determinístico
del flujo común de inbound. Una mención incidental de color dentro de una
consulta de catálogo no se persiste y el LLM no puede autorizar una captura.

## Acceso y aislamiento

Cada operación de lectura y borrado recibe ambos valores, `conversationId` y
`actorId`. Los adapters deben rechazar o devolver vacío ante identificadores
inválidos y no deben aceptar un teléfono como clave de memoria. La futura
persistencia deberá agregar control de concurrencia y, antes del multi-tenant,
una frontera explícita de `storeId`/`accountId`.

## Observabilidad segura

Los eventos pueden registrar operación, resultado, tamaño del contexto,
retención aplicada, latencia y error sanitizado. No deben registrar el cuerpo
del mensaje, teléfono, token, prompt completo, respuesta completa del modelo ni
payload completo del proveedor.

## Gate de aprobación

Antes de activar memoria persistente en producción se debe aprobar:

- período de retención legal y comercial;
- mecanismo de eliminación y auditoría;
- tratamiento de solicitudes de acceso/borrado;
- límites de acceso operativo;
- estrategia de backup y expiración;
- pruebas de aislamiento, expiración, borrado y recuperación.

Hasta completar ese gate, la memoria persistente permanece desactivada en
producción. El adapter PostgreSQL se verifica con Testcontainers y el adapter
no-op conserva los flujos disponibles sin retener estado. AgentCore Memory no
se incorpora como dependencia obligatoria.
